/*
 * @copyright defined in LICENSE.txt
 */

package hera.client;

import static hera.TransportConstants.ACCOUNT_GETSTATE_EITHER;
import static hera.TransportConstants.ACCOUNT_SIGN_EITHER;
import static hera.TransportConstants.ACCOUNT_VERIFY_EITHER;
import static hera.api.tupleorerror.Functions.identify;

import hera.ContextProvider;
import hera.ContextProviderInjectable;
import hera.annotation.ApiAudience;
import hera.annotation.ApiStability;
import hera.api.AccountEitherOperation;
import hera.api.model.Account;
import hera.api.model.AccountAddress;
import hera.api.model.AccountState;
import hera.api.model.Transaction;
import hera.api.tupleorerror.Function1;
import hera.api.tupleorerror.Function2;
import hera.api.tupleorerror.ResultOrError;
import hera.api.tupleorerror.ResultOrErrorFuture;
import hera.strategy.StrategyChain;
import io.grpc.ManagedChannel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@ApiAudience.Private
@ApiStability.Unstable
public class AccountEitherTemplate
    implements AccountEitherOperation, ChannelInjectable, ContextProviderInjectable {

  @Getter
  protected AccountBaseTemplate accountBaseTemplate = new AccountBaseTemplate();

  @Setter
  protected ContextProvider contextProvider;

  @Getter(lazy = true, value = AccessLevel.PROTECTED)
  private final StrategyChain strategyChain = StrategyChain.of(contextProvider.get());

  @Override
  public void setChannel(final ManagedChannel channel) {
    getAccountBaseTemplate().setChannel(channel);;
  }

  @Getter(lazy = true, value = AccessLevel.PROTECTED)
  private final Function1<AccountAddress, ResultOrErrorFuture<AccountState>> stateFunction =
      getStrategyChain()
          .apply(identify(getAccountBaseTemplate().getStateFunction(), ACCOUNT_GETSTATE_EITHER));
  @Getter(lazy = true, value = AccessLevel.PROTECTED)
  private final Function2<Account, Transaction, ResultOrErrorFuture<Transaction>> signFunction =
      getStrategyChain()
          .apply(identify(getAccountBaseTemplate().getSignFunction(), ACCOUNT_SIGN_EITHER));

  @Getter(lazy = true, value = AccessLevel.PROTECTED)
  private final Function2<Account, Transaction, ResultOrErrorFuture<Boolean>> verifyFunction =
      getStrategyChain()
          .apply(identify(getAccountBaseTemplate().getVerifyFunction(), ACCOUNT_VERIFY_EITHER));

  @Override
  public ResultOrError<AccountState> getState(final AccountAddress address) {
    return getStateFunction().apply(address).get();
  }

  @Override
  public ResultOrError<Transaction> sign(final Account account, final Transaction transaction) {
    return getSignFunction().apply(account, transaction).get();
  }

  @Override
  public ResultOrError<Boolean> verify(final Account account, final Transaction transaction) {
    return getVerifyFunction().apply(account, transaction).get();
  }

}
