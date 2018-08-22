/*
 * @copyright defined in LICENSE.txt
 */

package hera.client;

import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hera.AbstractTestCase;
import hera.api.AccountAsyncOperation;
import hera.api.model.Account;
import hera.api.model.AccountAddress;
import hera.api.model.AccountState;
import hera.api.tupleorerror.ResultOrError;
import hera.api.tupleorerror.ResultOrErrorFuture;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import types.AergoRPCServiceGrpc.AergoRPCServiceFutureStub;

@PrepareForTest({AergoRPCServiceFutureStub.class})
public class AccountTemplateTest extends AbstractTestCase {

  protected final AccountAddress ACCOUNT_ADDRESS =
      AccountAddress.of(randomUUID().toString().getBytes());

  protected final String PASSWORD = randomUUID().toString();

  @Test
  public void testList() throws Exception {
    ResultOrErrorFuture<List<Account>> futureMock = mock(ResultOrErrorFuture.class);
    when(futureMock.get(anyLong(), any())).thenReturn(mock(ResultOrError.class));
    AccountAsyncOperation asyncOperationMock = mock(AccountAsyncOperation.class);
    when(asyncOperationMock.list()).thenReturn(futureMock);

    final AccountTemplate accountTemplate = new AccountTemplate(asyncOperationMock);

    final ResultOrError<List<Account>> accountListFuture = accountTemplate.list();
    assertNotNull(accountListFuture);
  }

  @Test
  public void testCreate() throws Exception {
    ResultOrErrorFuture<Account> futureMock = mock(ResultOrErrorFuture.class);
    when(futureMock.get(anyLong(), any())).thenReturn(mock(ResultOrError.class));
    AccountAsyncOperation asyncOperationMock = mock(AccountAsyncOperation.class);
    when(asyncOperationMock.create(anyString())).thenReturn(futureMock);

    final AccountTemplate accountTemplate = new AccountTemplate(asyncOperationMock);

    final ResultOrError<Account> createdAccount = accountTemplate.create(randomUUID().toString());
    assertNotNull(createdAccount);
  }

  @Test
  public void testGet() throws Exception {
    ResultOrErrorFuture<Optional<Account>> futureMock = mock(ResultOrErrorFuture.class);
    when(futureMock.get(anyLong(), any())).thenReturn(mock(ResultOrError.class));
    AccountAsyncOperation asyncOperationMock = mock(AccountAsyncOperation.class);
    when(asyncOperationMock.get(any())).thenReturn(futureMock);

    final AccountTemplate accountTemplate = new AccountTemplate(asyncOperationMock);

    final ResultOrError<Optional<Account>> account = accountTemplate.get(ACCOUNT_ADDRESS);
    assertNotNull(account);
  }

  @Test
  public void testLock() throws Exception {
    ResultOrErrorFuture<Boolean> futureMock = mock(ResultOrErrorFuture.class);
    when(futureMock.get(anyLong(), any())).thenReturn(mock(ResultOrError.class));
    AccountAsyncOperation asyncOperationMock = mock(AccountAsyncOperation.class);
    when(asyncOperationMock.lock(any(), anyString())).thenReturn(futureMock);

    final AccountTemplate accountTemplate = new AccountTemplate(asyncOperationMock);

    ResultOrError<Boolean> lockResult = accountTemplate.lock(ACCOUNT_ADDRESS, PASSWORD);
    assertNotNull(lockResult);
  }

  @Test
  public void testUnlock() throws Exception {
    ResultOrErrorFuture<Boolean> futureMock = mock(ResultOrErrorFuture.class);
    when(futureMock.get(anyLong(), any())).thenReturn(mock(ResultOrError.class));
    AccountAsyncOperation asyncOperationMock = mock(AccountAsyncOperation.class);
    when(asyncOperationMock.unlock(any(), anyString())).thenReturn(futureMock);

    final AccountTemplate accountTemplate = new AccountTemplate(asyncOperationMock);

    ResultOrError<Boolean> lockResult = accountTemplate.unlock(ACCOUNT_ADDRESS, PASSWORD);
    assertNotNull(lockResult);
  }

  @Test
  public void testGetState() throws Exception {
    ResultOrErrorFuture<Optional<AccountState>> futureMock = mock(ResultOrErrorFuture.class);
    when(futureMock.get(anyLong(), any())).thenReturn(mock(ResultOrError.class));
    AccountAsyncOperation asyncOperationMock = mock(AccountAsyncOperation.class);
    when(asyncOperationMock.getState(any())).thenReturn(futureMock);

    final AccountTemplate accountTemplate = new AccountTemplate(asyncOperationMock);

    final ResultOrError<Optional<AccountState>> createdAccount =
        accountTemplate.getState(ACCOUNT_ADDRESS);
    assertNotNull(createdAccount);
  }

}
