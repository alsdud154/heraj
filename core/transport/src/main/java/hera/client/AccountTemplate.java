/*
 * @copyright defined in LICENSE.txt
 */

package hera.client;

import hera.Context;
import hera.ContextStorage;
import hera.api.AccountOperation;
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
import hera.key.Signer;
import java.util.Arrays;
import java.util.List;

class AccountTemplate extends AbstractTemplate implements AccountOperation {

  protected final AccountMethods accountMethods = new AccountMethods();

  AccountTemplate(final ContextStorage<Context> contextStorage) {
    super(contextStorage);
  }

  @Override
  public AccountState getState(Account account) {
    return getState(account.getAddress());
  }

  @Override
  public AccountState getState(final AccountAddress address) {
    return request(accountMethods.getAccountState(), Arrays.<Object>asList(address));
  }

  @Override
  public TxHash createName(final Account account, final String name, final long nonce) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TxHash createName(final Signer signer, final String name, final long nonce) {
    return request(accountMethods.getCreateName(), Arrays.<Object>asList(signer, name, nonce));
  }

  @Override
  public TxHash updateName(final Account owner, final String name, final AccountAddress newOwner,
      final long nonce) {
    throw new UnsupportedOperationException("Use Signer instead");
  }

  @Override
  public TxHash updateName(final Signer signer, final String name, final AccountAddress newOwner,
      final long nonce) {
    return request(accountMethods.getUpdateName(),
        Arrays.asList(signer, name, newOwner, nonce));
  }

  @Override
  public AccountAddress getNameOwner(final String name) {
    return getNameOwner(name, 0);
  }

  @Override
  public AccountAddress getNameOwner(final String name, final long blockNumber) {
    return request(accountMethods.getNameOwner(), Arrays.<Object>asList(name, blockNumber));
  }

  @Override
  public TxHash stake(final Account account, final Aer amount, final long nonce) {
    throw new UnsupportedOperationException("Use Signer instead");
  }

  @Override
  public TxHash stake(final Signer signer, final Aer amount, final long nonce) {
    return request(accountMethods.getStake(), Arrays.<Object>asList(signer, amount, nonce));
  }

  @Override
  public TxHash unstake(Account account, Aer amount, long nonce) {
    throw new UnsupportedOperationException("Use Signer instead");
  }

  @Override
  public TxHash unstake(final Signer signer, final Aer amount, final long nonce) {
    return request(accountMethods.getUnstake(), Arrays.<Object>asList(signer, amount, nonce));
  }

  @Override
  public StakeInfo getStakingInfo(final AccountAddress accountAddress) {
    return request(accountMethods.getStakeInfo(), Arrays.<Object>asList(accountAddress));
  }

  @Override
  public TxHash vote(final Signer signer, final String voteId, final List<String> candidates,
      final long nonce) {
    return request(accountMethods.getVote(),
        Arrays.<Object>asList(signer, voteId, candidates, nonce));
  }

  @Override
  public AccountTotalVote getVotesOf(final AccountAddress accountAddress) {
    return request(accountMethods.getVoteOf(), Arrays.<Object>asList(accountAddress));
  }

  @Override
  public List<ElectedCandidate> listElected(final String voteId, final int showCount) {
    return request(accountMethods.getListElected(), Arrays.<Object>asList(voteId, showCount));
  }

  @Override
  public Transaction sign(final Account account, final RawTransaction rawTransaction) {
    throw new UnsupportedOperationException("Use AergoKey instead");
  }

  @Override
  public boolean verify(final Account account, final Transaction transaction) {
    throw new UnsupportedOperationException("Use AergoSignVerifier instead");
  }

}
