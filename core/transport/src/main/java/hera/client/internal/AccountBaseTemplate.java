/*
 * @copyright defined in LICENSE.txt
 */

package hera.client.internal;

import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static org.slf4j.LoggerFactory.getLogger;
import static types.AergoRPCServiceGrpc.newFutureStub;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import hera.ContextProvider;
import hera.ContextProviderInjectable;
import hera.annotation.ApiAudience;
import hera.annotation.ApiStability;
import hera.api.function.Function1;
import hera.api.function.Function2;
import hera.api.function.Function3;
import hera.api.function.Function4;
import hera.api.model.Account;
import hera.api.model.AccountAddress;
import hera.api.model.AccountState;
import hera.api.model.AccountTotalVote;
import hera.api.model.Aer;
import hera.api.model.ElectedCandidate;
import hera.api.model.RawTransaction;
import hera.api.model.StakeInfo;
import hera.api.model.Transaction;
import hera.api.model.TxHash;
import hera.client.ChannelInjectable;
import hera.exception.TransactionVerificationException;
import hera.key.Signer;
import hera.transaction.TxSigner;
import hera.transport.AccountAddressConverterFactory;
import hera.transport.AccountStateConverterFactory;
import hera.transport.AccountTotalVoteConverterFactory;
import hera.transport.ElectedCandidateConverterFactory;
import hera.transport.ModelConverter;
import hera.transport.StakeInfoConverterFactory;
import hera.transport.TransactionConverterFactory;
import io.grpc.ManagedChannel;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.slf4j.Logger;
import types.AergoRPCServiceGrpc.AergoRPCServiceFutureStub;
import types.Blockchain;
import types.Rpc;

@ApiAudience.Private
@ApiStability.Unstable
public class AccountBaseTemplate implements ChannelInjectable, ContextProviderInjectable {

  protected final transient Logger logger = getLogger(getClass());

  protected final ModelConverter<AccountAddress, ByteString> accountAddressConverter =
      new AccountAddressConverterFactory().create();

  protected final ModelConverter<AccountState, Blockchain.State> accountStateConverter =
      new AccountStateConverterFactory().create();

  protected final ModelConverter<StakeInfo, Rpc.Staking> stakingInfoConverter =
      new StakeInfoConverterFactory().create();

  protected final ModelConverter<Transaction, Blockchain.Tx> transactionConverter =
      new TransactionConverterFactory().create();

  protected final ModelConverter<ElectedCandidate, Rpc.Vote> electedCandidateConverter =
      new ElectedCandidateConverterFactory().create();

  protected final ModelConverter<AccountTotalVote, Rpc.AccountVoteInfo> accountTotalVoteConverter =
      new AccountTotalVoteConverterFactory().create();

  @Getter
  protected AergoRPCServiceFutureStub aergoService;

  protected ContextProvider contextProvider;

  protected TransactionBaseTemplate transactionBaseTemplate = new TransactionBaseTemplate();

  @Override
  public void setContextProvider(final ContextProvider contextProvider) {
    this.contextProvider = contextProvider;
    transactionBaseTemplate.setContextProvider(contextProvider);
  }

  @Override
  public void setChannel(final ManagedChannel channel) {
    this.aergoService = newFutureStub(channel);
    transactionBaseTemplate.setChannel(channel);
  }

  @Getter
  private final Function1<AccountAddress, FinishableFuture<AccountState>> stateFunction =
      new Function1<AccountAddress, FinishableFuture<AccountState>>() {

        @Override
        public FinishableFuture<AccountState> apply(final AccountAddress address) {
          logger.debug("GetState with {}", address);

          FinishableFuture<AccountState> nextFuture = new FinishableFuture<AccountState>();
          try {
            final Rpc.SingleBytes rpcAddress = Rpc.SingleBytes.newBuilder()
                .setValue(accountAddressConverter.convertToRpcModel(address))
                .build();
            logger.trace("AergoService getstate arg: {}", rpcAddress);

            ListenableFuture<Blockchain.State> listenableFuture = aergoService.getState(rpcAddress);
            FutureChain<Blockchain.State, AccountState> callback =
                new FutureChain<Blockchain.State, AccountState>(nextFuture, contextProvider.get());
            callback.setSuccessHandler(new Function1<Blockchain.State, AccountState>() {
              @Override
              public AccountState apply(final Blockchain.State state) {
                final AccountState withoutAddress =
                    accountStateConverter.convertToDomainModel(state);
                return new AccountState(address, withoutAddress.getNonce(),
                    withoutAddress.getBalance());
              }
            });
            addCallback(listenableFuture, callback, directExecutor());
          } catch (Exception e) {
            nextFuture.fail(e);
          }
          return nextFuture;
        }
      };

  @Getter
  private final Function3<Account, String, Long,
      FinishableFuture<TxHash>> deprecatedCreateNameFunction = new Function3<
          Account, String, Long, FinishableFuture<TxHash>>() {

        @Override
        public FinishableFuture<TxHash> apply(final Account account, final String name,
            final Long nonce) {
          logger.debug("Create account name to account: {}, name: {}, nonce: {}",
              account.getAddress(), name, nonce);

          final RawTransaction rawTransaction = RawTransaction.newCreateNameTxBuilder()
              .chainIdHash(contextProvider.get().getChainIdHash())
              .from(account.getAddress())
              .nonce(nonce)
              .name(name)
              .build();
          final Transaction signed = getSignFunction().apply(account, rawTransaction).get();
          return transactionBaseTemplate.getCommitFunction().apply(signed);
        }
      };

  @Getter
  private final Function3<Signer, String, Long, FinishableFuture<TxHash>> createNameFunction =
      new Function3<Signer, String, Long, FinishableFuture<TxHash>>() {

        @Override
        public FinishableFuture<TxHash> apply(final Signer signer, final String name,
            final Long nonce) {
          logger.debug("Create account name with signer: {}, name: {}, nonce: {}",
              signer.getPrincipal(), name, nonce);

          final RawTransaction rawTransaction = RawTransaction.newCreateNameTxBuilder()
              .chainIdHash(contextProvider.get().getChainIdHash())
              .from(signer.getPrincipal())
              .nonce(nonce)
              .name(name)
              .build();
          final Transaction signed = signer.sign(rawTransaction);
          return transactionBaseTemplate.getCommitFunction().apply(signed);
        }
      };

  @Getter
  private final Function4<Account, String, AccountAddress, Long,
      FinishableFuture<TxHash>> deprecatedUpdateNameFunction = new Function4<Account, String,
          AccountAddress, Long, FinishableFuture<TxHash>>() {

        @Override
        public FinishableFuture<TxHash> apply(final Account owner, final String name,
            final AccountAddress newOwner, final Long nonce) {
          logger.debug("Update account name from account: {}, name: {}, to account: {}, nonce: {}",
              owner.getAddress(), name, newOwner, nonce);

          final RawTransaction rawTransaction = RawTransaction.newUpdateNameTxBuilder()
              .chainIdHash(contextProvider.get().getChainIdHash())
              .from(owner.getAddress())
              .nonce(nonce)
              .name(name)
              .newOwner(newOwner)
              .build();
          final Transaction signed = getSignFunction().apply(owner, rawTransaction).get();
          return transactionBaseTemplate.getCommitFunction().apply(signed);
        }
      };

  @Getter
  private final Function4<Signer, String, AccountAddress, Long,
      FinishableFuture<TxHash>> updateNameFunction = new Function4<Signer, String,
          AccountAddress, Long, FinishableFuture<TxHash>>() {

        @Override
        public FinishableFuture<TxHash> apply(final Signer signer, final String name,
            final AccountAddress newOwner, final Long nonce) {
          logger.debug("Update account name with signer: {}, name: {}, to account: {}, nonce: {}",
              signer, name, newOwner, nonce);

          final RawTransaction rawTransaction = RawTransaction.newUpdateNameTxBuilder()
              .chainIdHash(contextProvider.get().getChainIdHash())
              .from(signer.getPrincipal())
              .nonce(nonce)
              .name(name)
              .newOwner(newOwner)
              .build();
          final Transaction signed = signer.sign(rawTransaction);
          return transactionBaseTemplate.getCommitFunction().apply(signed);
        }
      };

  @Getter
  private final Function1<String, FinishableFuture<AccountAddress>> getNameOwnerFunction =
      new Function1<String, FinishableFuture<AccountAddress>>() {

        @Override
        public FinishableFuture<AccountAddress> apply(final String name) {
          logger.debug("Get name owner of name: {}", name);

          FinishableFuture<AccountAddress> nextFuture = new FinishableFuture<AccountAddress>();
          try {
            final Rpc.Name rpcName = Rpc.Name.newBuilder().setName(name).build();
            logger.trace("AergoService getNameInfo arg: {}", rpcName);

            ListenableFuture<Rpc.NameInfo> listenableFuture = aergoService.getNameInfo(rpcName);
            FutureChain<Rpc.NameInfo, AccountAddress> callback =
                new FutureChain<Rpc.NameInfo, AccountAddress>(nextFuture, contextProvider.get());
            callback.setSuccessHandler(new Function1<Rpc.NameInfo, AccountAddress>() {
              @Override
              public AccountAddress apply(final Rpc.NameInfo nameInfo) {
                return accountAddressConverter.convertToDomainModel(nameInfo.getOwner());
              }
            });
            addCallback(listenableFuture, callback, directExecutor());
          } catch (Exception e) {
            nextFuture.fail(e);
          }
          return nextFuture;
        }
      };

  @Getter
  private final Function3<Account, Aer, Long, FinishableFuture<TxHash>> deprecatedStakingFunction =
      new Function3<Account, Aer, Long, FinishableFuture<TxHash>>() {

        @Override
        public FinishableFuture<TxHash> apply(final Account account, final Aer amount,
            final Long nonce) {
          logger.debug("Staking account with account: {}, amount: {}, nonce: {}",
              account.getAddress(), amount, nonce);

          final RawTransaction rawTransaction = RawTransaction.newStakeTxBuilder()
              .chainIdHash(contextProvider.get().getChainIdHash())
              .from(account.getAddress())
              .amount(amount)
              .nonce(nonce)
              .build();
          final Transaction signed = getSignFunction().apply(account, rawTransaction).get();
          return transactionBaseTemplate.getCommitFunction().apply(signed);
        }
      };

  @Getter
  private final Function3<Signer, Aer, Long, FinishableFuture<TxHash>> stakingFunction =
      new Function3<Signer, Aer, Long, FinishableFuture<TxHash>>() {

        @Override
        public FinishableFuture<TxHash> apply(final Signer signer, final Aer amount,
            final Long nonce) {
          logger.debug("Staking account with signer: {}, amount: {}, nonce: {}",
              signer.getPrincipal(), amount, nonce);

          final RawTransaction rawTransaction = RawTransaction.newStakeTxBuilder()
              .chainIdHash(contextProvider.get().getChainIdHash())
              .from(signer.getPrincipal())
              .amount(amount)
              .nonce(nonce)
              .build();
          final Transaction signed = signer.sign(rawTransaction);
          return transactionBaseTemplate.getCommitFunction().apply(signed);
        }
      };

  @Getter
  private final Function3<Account, Aer, Long,
      FinishableFuture<TxHash>> deprecatedUnstakingFunction = new Function3<Account,
          Aer, Long, FinishableFuture<TxHash>>() {

        @Override
        public FinishableFuture<TxHash> apply(final Account account, final Aer amount,
            final Long nonce) {
          logger.debug("Unstaking account with account: {}, amount: {}, nonce: {}",
              account.getAddress(), amount, nonce);

          final RawTransaction rawTransaction = RawTransaction.newUnstakeTxBuilder()
              .chainIdHash(contextProvider.get().getChainIdHash())
              .from(account.getAddress())
              .amount(amount)
              .nonce(nonce)
              .build();
          final Transaction signed = getSignFunction().apply(account, rawTransaction).get();
          return transactionBaseTemplate.getCommitFunction().apply(signed);
        }
      };

  @Getter
  private final Function3<Signer, Aer, Long, FinishableFuture<TxHash>> unstakingFunction =
      new Function3<Signer, Aer, Long, FinishableFuture<TxHash>>() {

        @Override
        public FinishableFuture<TxHash> apply(final Signer signer, final Aer amount,
            final Long nonce) {
          logger.debug("Unstaking account with signer: {}, amount: {}, nonce: {}",
              signer.getPrincipal(), amount, nonce);

          final RawTransaction rawTransaction = RawTransaction.newUnstakeTxBuilder()
              .chainIdHash(contextProvider.get().getChainIdHash())
              .from(signer.getPrincipal())
              .amount(amount)
              .nonce(nonce)
              .build();
          final Transaction signed = signer.sign(rawTransaction);
          return transactionBaseTemplate.getCommitFunction().apply(signed);
        }
      };

  @Getter
  private final Function1<AccountAddress, FinishableFuture<StakeInfo>> stakingInfoFunction =
      new Function1<AccountAddress, FinishableFuture<StakeInfo>>() {

        @Override
        public FinishableFuture<StakeInfo> apply(final AccountAddress accountAddress) {
          logger.debug("Get staking information with address: {}", accountAddress);

          FinishableFuture<StakeInfo> nextFuture = new FinishableFuture<StakeInfo>();
          try {
            final Rpc.AccountAddress rpcAddress = Rpc.AccountAddress.newBuilder()
                .setValue(accountAddressConverter.convertToRpcModel(accountAddress))
                .build();
            logger.trace("AergoService getStaking arg: {}", rpcAddress);

            ListenableFuture<Rpc.Staking> listenableFuture = aergoService.getStaking(rpcAddress);
            FutureChain<Rpc.Staking, StakeInfo> callback =
                new FutureChain<Rpc.Staking, StakeInfo>(nextFuture, contextProvider.get());
            callback.setSuccessHandler(new Function1<Rpc.Staking, StakeInfo>() {
              @Override
              public StakeInfo apply(final Rpc.Staking rpcStaking) {
                final StakeInfo withoutAddress =
                    stakingInfoConverter.convertToDomainModel(rpcStaking);
                return new StakeInfo(accountAddress, withoutAddress.getAmount(),
                    withoutAddress.getBlockNumber());
              }
            });
            addCallback(listenableFuture, callback, directExecutor());
          } catch (Exception e) {
            nextFuture.fail(e);
          }
          return nextFuture;
        }
      };

  @Getter
  private final Function2<Account, RawTransaction, FinishableFuture<Transaction>> signFunction =
      new Function2<Account, RawTransaction, FinishableFuture<Transaction>>() {

        @Override
        public FinishableFuture<Transaction> apply(final Account account,
            final RawTransaction rawTransaction) {
          logger.debug("Sign request with account: {}, rawTx: {}", account.getAddress(),
              rawTransaction);

          FinishableFuture<Transaction> nextFuture = new FinishableFuture<Transaction>();
          if (account instanceof TxSigner) {
            final TxSigner signer = (TxSigner) account;
            final Transaction signed = signer.sign(rawTransaction);
            nextFuture.success(signed);
          } else {
            final Transaction domainTransaction =
                new Transaction(rawTransaction, null, null, null, 0, false);
            final Blockchain.Tx rpcTx = transactionConverter.convertToRpcModel(domainTransaction);
            logger.trace("AergoService signTX arg: {}", rpcTx);

            ListenableFuture<Blockchain.Tx> listenableFuture = aergoService.signTX(rpcTx);
            FutureChain<Blockchain.Tx, Transaction> callback =
                new FutureChain<Blockchain.Tx, Transaction>(nextFuture, contextProvider.get());
            callback.setSuccessHandler(new Function1<Blockchain.Tx, Transaction>() {
              @Override
              public Transaction apply(final Blockchain.Tx tx) {
                return transactionConverter.convertToDomainModel(tx);
              }
            });
            addCallback(listenableFuture, callback, directExecutor());
          }
          return nextFuture;
        }
      };

  @Getter
  private final Function2<Account, Transaction, FinishableFuture<Boolean>> verifyFunction =
      new Function2<Account, Transaction, FinishableFuture<Boolean>>() {

        @Override
        public FinishableFuture<Boolean> apply(final Account account,
            final Transaction transaction) {
          logger.debug("Sign verify with account: {}, signedTx: {}", account, transaction);

          FinishableFuture<Boolean> nextFuture = new FinishableFuture<Boolean>();
          try {
            final Blockchain.Tx rpcTx = transactionConverter.convertToRpcModel(transaction);
            logger.trace("AergoService verifyTX arg: {}", rpcTx);

            ListenableFuture<Rpc.VerifyResult> listenableFuture = aergoService.verifyTX(rpcTx);
            FutureChain<Rpc.VerifyResult, Boolean> callback =
                new FutureChain<Rpc.VerifyResult, Boolean>(nextFuture, contextProvider.get());
            callback.setSuccessHandler(new Function1<Rpc.VerifyResult, Boolean>() {

              @Override
              public Boolean apply(final Rpc.VerifyResult result) {
                if (Rpc.VerifyStatus.VERIFY_STATUS_OK != result.getError()) {
                  new TransactionVerificationException(result.getError());
                }
                return true;
              }
            });
            addCallback(listenableFuture, callback, directExecutor());
          } catch (Exception e) {
            nextFuture.fail(e);
          }
          return nextFuture;
        }
      };

  @Getter
  private final Function4<Signer, String, List<String>, Long,
      FinishableFuture<TxHash>> voteFunction = new Function4<
          Signer, String, List<String>, Long, FinishableFuture<TxHash>>() {

        @Override
        public FinishableFuture<TxHash> apply(final Signer signer, final String voteId,
            final List<String> candidates, final Long nonce) {
          logger.debug("Voting with signer: }, voteId: {}, candidates: {}, nonce: {}",
              signer.getPrincipal(), voteId, candidates, nonce);

          final RawTransaction rawTransaction = RawTransaction.newVoteTxBuilder()
              .chainIdHash(contextProvider.get().getChainIdHash())
              .from(signer.getPrincipal())
              .nonce(nonce)
              .voteId(voteId)
              .candidates(candidates)
              .build();
          final Transaction signed = signer.sign(rawTransaction);
          return transactionBaseTemplate.getCommitFunction().apply(signed);
        }
      };

  @Getter
  private final Function2<String, Integer,
      FinishableFuture<List<ElectedCandidate>>> listElectedFunction = new Function2<
          String, Integer, FinishableFuture<List<ElectedCandidate>>>() {

        @Override
        public FinishableFuture<List<ElectedCandidate>> apply(final String voteId,
            final Integer showCount) {
          logger.debug("Get votes status with voteId: {}, showCount: {}", voteId, showCount);

          FinishableFuture<List<ElectedCandidate>> nextFuture =
              new FinishableFuture<List<ElectedCandidate>>();
          try {
            final Rpc.VoteParams rpcVoteParams = Rpc.VoteParams.newBuilder()
                .setId(voteId)
                .setCount(showCount)
                .build();
            logger.trace("AergoService getVotes arg: {}", rpcVoteParams);

            ListenableFuture<Rpc.VoteList> listenableFuture = aergoService.getVotes(rpcVoteParams);
            FutureChain<Rpc.VoteList, List<ElectedCandidate>> callback =
                new FutureChain<Rpc.VoteList, List<ElectedCandidate>>(nextFuture,
                    contextProvider.get());
            callback.setSuccessHandler(new Function1<Rpc.VoteList, List<ElectedCandidate>>() {

              @Override
              public List<ElectedCandidate> apply(final Rpc.VoteList rpcVoteList) {
                final List<ElectedCandidate> electedCandidates = new ArrayList<ElectedCandidate>();
                for (final Rpc.Vote rpcCandidate : rpcVoteList.getVotesList()) {
                  final ElectedCandidate domainElectedCandidate =
                      electedCandidateConverter.convertToDomainModel(rpcCandidate);
                  electedCandidates.add(domainElectedCandidate);
                }
                return electedCandidates;
              }
            });
            addCallback(listenableFuture, callback, directExecutor());
          } catch (Exception e) {
            nextFuture.fail(e);
          }
          return nextFuture;
        }
      };

  @Getter
  private final Function1<AccountAddress, FinishableFuture<AccountTotalVote>> votesOfFunction =
      new Function1<AccountAddress, FinishableFuture<AccountTotalVote>>() {

        @Override
        public FinishableFuture<AccountTotalVote> apply(final AccountAddress accountAddress) {
          logger.debug("Get votes with address: {}", accountAddress);

          FinishableFuture<AccountTotalVote> nextFuture = new FinishableFuture<AccountTotalVote>();
          try {
            final Rpc.AccountAddress rpcAddress = Rpc.AccountAddress.newBuilder()
                .setValue(accountAddressConverter.convertToRpcModel(accountAddress))
                .build();
            logger.trace("AergoService getAccountVotes arg: {}", rpcAddress);

            ListenableFuture<Rpc.AccountVoteInfo> listenableFuture =
                aergoService.getAccountVotes(rpcAddress);
            FutureChain<Rpc.AccountVoteInfo, AccountTotalVote> callback =
                new FutureChain<Rpc.AccountVoteInfo, AccountTotalVote>(nextFuture,
                    contextProvider.get());
            callback.setSuccessHandler(new Function1<Rpc.AccountVoteInfo, AccountTotalVote>() {

              @Override
              public AccountTotalVote apply(final Rpc.AccountVoteInfo rpcAccountVoteTotal) {
                return accountTotalVoteConverter.convertToDomainModel(rpcAccountVoteTotal);
              }
            });
            addCallback(listenableFuture, callback, directExecutor());
          } catch (Exception e) {
            nextFuture.fail(e);
          }
          return nextFuture;
        }
      };

}