/*
 * @copyright defined in LICENSE.txt
 */

package hera.client;

import hera.Context;
import hera.annotation.ApiAudience;
import hera.annotation.ApiStability;
import hera.api.SignAsyncOperation;
import hera.api.SignEitherOperation;
import hera.api.SignOperation;
import hera.api.model.Signature;
import hera.api.model.Transaction;
import hera.key.AergoKey;
import io.grpc.ManagedChannel;
import java.util.Optional;

@ApiAudience.Private
@ApiStability.Unstable
public class SignTemplate implements SignOperation, ChannelInjectable {

  protected Context context;

  protected SignEitherTemplate signEitherOperation = new SignEitherTemplate();

  @Override
  public void setContext(final Context context) {
    this.context = context;
    signEitherOperation.setContext(context);
  }

  @Override
  public void injectChannel(final ManagedChannel channel) {
    signEitherOperation.injectChannel(channel);
  }

  @Override
  public Signature sign(final AergoKey key, final Transaction transaction) {
    return signEitherOperation.sign(key, transaction).getResult();
  }

  @Override
  public boolean verify(final AergoKey key, final Transaction transaction) {
    return signEitherOperation.verify(key, transaction).getResult();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> Optional<T> adapt(final Class<T> adaptor) {
    if (adaptor.isAssignableFrom(SignOperation.class)) {
      return (Optional<T>) Optional.of(this);
    } else if (adaptor.isAssignableFrom(SignEitherOperation.class)) {
      return (Optional<T>) Optional.of(signEitherOperation);
    } else if (adaptor.isAssignableFrom(SignAsyncOperation.class)) {
      return (Optional<T>) signEitherOperation.adapt(SignAsyncOperation.class);
    }
    return Optional.empty();
  }

}
