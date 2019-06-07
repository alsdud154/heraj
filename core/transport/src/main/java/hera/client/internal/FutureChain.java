/*
 * @copyright defined in LICENSE.txt
 */

package hera.client.internal;

import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.util.concurrent.FutureCallback;
import hera.Context;
import hera.ContextHolder;
import hera.api.function.Function1;
import hera.exception.RpcException;
import hera.exception.RpcExceptionConverter;
import hera.util.ExceptionConverter;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;

@RequiredArgsConstructor
public class FutureChain<T, R> implements FutureCallback<T> {

  protected static final String ASYNC_CALL_MESSAGE =
      "============================== Async call ==============================";

  protected final transient Logger logger = getLogger(getClass());

  // store Exception since getting stack trace element object is too expensive
  protected Exception stackTraceHolder = new Exception();

  protected ExceptionConverter<RpcException> exceptionConverter = new RpcExceptionConverter();

  @NonNull
  protected final FinishableFuture<R> nextFuture;

  // hold main thread context
  @NonNull
  protected final Context sourceContext;

  @Setter
  protected Function1<T, R> successHandler;

  @Setter
  protected Function1<Throwable, R> failureHandler = null;

  @Override
  public void onSuccess(@Nullable T t) {
    connectAsyncContextWithSourceContext();
    logger.trace("Async request success result: {}, context: {}", t, ContextHolder.get(this));
    try {
      final R handled = successHandler.apply(t);
      nextFuture.success(handled);
    } catch (Exception e) {
      failNext(exceptionConverter.convert(e));
    }
  }

  @Override
  public void onFailure(final Throwable error) {
    connectAsyncContextWithSourceContext();
    logger.trace("Async request fail result: {}, context: {}", error.toString(),
        ContextHolder.get(this));
    try {
      if (null != failureHandler) {
        final R handled = failureHandler.apply(error);
        nextFuture.success(handled);
      } else {
        failNext(exceptionConverter.convert(error));
      }
    } catch (Exception e) {
      failNext(exceptionConverter.convert(e));
    }
  }

  protected void connectAsyncContextWithSourceContext() {
    ContextHolder.set(this, sourceContext);
  }

  protected void failNext(final RpcException wrapped) {
    wrapped.setStackTrace(concatStackTrace(wrapped, stackTraceHolder));
    nextFuture.fail(wrapped);
  }

  protected StackTraceElement[] concatStackTrace(final Throwable asyncStack,
      final Throwable callerStack) {
    final StackTraceElement[] asyncStackTrace = asyncStack.getStackTrace();
    final StackTraceElement[] callerStackTrace = callerStack.getStackTrace();

    // remove higher thread stack : 3
    final int asyncStackLength = asyncStackTrace.length - 3;
    // remove FutureChain <init> stack : 1
    final int callerStackLength = callerStackTrace.length - 1;

    final StackTraceElement[] concated =
        new StackTraceElement[asyncStackLength + 1 + callerStackLength];
    System.arraycopy(asyncStackTrace, 0, concated, 0, asyncStackLength);
    concated[asyncStackLength] = new StackTraceElement(ASYNC_CALL_MESSAGE, "", null, 0);
    System.arraycopy(callerStackTrace, 1, concated, asyncStackLength + 1, callerStackLength);

    return concated;
  }

}