/*
 * @copyright defined in LICENSE.txt
 */

package hera.wallet;

import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import hera.AbstractTestCase;
import hera.api.model.Aer;
import hera.api.model.Authentication;
import hera.api.model.BytesValue;
import hera.api.model.ChainIdHash;
import hera.api.model.Hash;
import hera.api.model.RawTransaction;
import hera.api.model.Signature;
import hera.api.model.Time;
import hera.client.AergoClient;
import hera.client.AergoClientBuilder;
import hera.exception.HerajException;
import hera.key.AergoKey;
import hera.key.AergoKeyGenerator;
import hera.keystore.InMemoryKeyStore;
import hera.keystore.KeyStore;
import hera.model.KeyAlias;
import hera.util.Sha256Utils;
import org.junit.Test;

public class WalletApiImplTest extends AbstractTestCase {

  protected final Authentication valid = Authentication
      .of(KeyAlias.of(randomUUID().toString().replace("-", "")), randomUUID().toString());
  protected final AergoKey storedKey = new AergoKeyGenerator().create();

  protected WalletApi supplyWalletApi() {
    final KeyStore keyStore = new InMemoryKeyStore();
    keyStore.save(valid, storedKey);
    final TryCountAndInterval tryCountAndInterval = TryCountAndInterval.of(3, Time.of(1000L));
    return new WalletApiImpl(keyStore, tryCountAndInterval);
  }

  @Test
  public void testObjectBasic() {
    // when
    final WalletApi walletApi1 = supplyWalletApi();
    final WalletApi walletApi2 = supplyWalletApi();

    // to string
    assertNotNull(walletApi1.toString());

    // equals
    assertNotEquals(walletApi1, walletApi2);

    // hashcode
    assertEquals(walletApi1.hashCode(), walletApi1.hashCode());
    assertNotEquals(walletApi1.hashCode(), walletApi2.hashCode());
  }

  @Test
  public void testBind() {
    // when
    final WalletApi walletApi = supplyWalletApi();

    // then
    final AergoClient aergoClient = new AergoClientBuilder().build();
    walletApi.bind(aergoClient);
  }

  @Test
  public void shouldBindThrowErrorOnNull() {
    // when
    final WalletApi walletApi = supplyWalletApi();

    // then
    try {
      walletApi.bind(null);
      fail();
    } catch (Throwable e) {
      // good we expected this
    }
  }

  @Test
  public void testUnlock() {
    // when
    final WalletApi walletApi = supplyWalletApi();
    walletApi.bind(mock(AergoClient.class));

    // then
    assertTrue(walletApi.unlock(valid));
  }

  @Test
  public void shouldUnlockTwiceFail() {
    try {
      // given
      final WalletApi walletApi = supplyWalletApi();
      walletApi.bind(mock(AergoClient.class));
      walletApi.unlock(valid);
      // when
      walletApi.unlock(valid);
      fail();
    } catch (Exception e) {
      // then
    }
  }

  @Test
  public void shouldUnlockFailOnInvalidOne() {
    // when
    final WalletApi walletApi = supplyWalletApi();
    walletApi.bind(mock(AergoClient.class));
    final Authentication invalid =
        Authentication.of(new KeyAlias("invalid"), randomUUID().toString());

    // then
    assertFalse(walletApi.unlock(invalid));
  }

  @Test
  public void testLock() {
    // when
    final WalletApi walletApi = supplyWalletApi();
    walletApi.unlock(valid);

    // then
    assertTrue(walletApi.lock(valid));
  }

  @Test
  public void shouldLockFailOnLockedOne() {
    // when
    final WalletApi walletApi = supplyWalletApi();
    walletApi.unlock(valid);
    walletApi.lock(valid);

    // then
    assertFalse(walletApi.lock(valid));
  }

  @Test
  public void shouldLockFailOnInvalid() {
    // when
    final WalletApi walletApi = supplyWalletApi();
    walletApi.unlock(valid);

    // then
    final Authentication invalid =
        Authentication.of(new KeyAlias("invalid"), randomUUID().toString());
    assertFalse(walletApi.lock(invalid));
  }

  @Test
  public void testGetApi() {
    final WalletApi walletApi = supplyWalletApi();
    walletApi.bind(mock(AergoClient.class));
    assertNotNull(walletApi.transactionApi());
    assertNotNull(walletApi.queryApi());
  }

  @Test
  public void shouldGetApiThrowExceptionOnClientNotBinded() {
    // when
    final WalletApi walletApi = supplyWalletApi();

    // then
    try {
      walletApi.transactionApi();
    } catch (HerajException e) {
      // good we expected this
    }

    // and then
    try {
      walletApi.queryApi();
    } catch (HerajException e) {
      // good we expected this
    }
  }

  @Test
  public void testSign() {
    // when
    final WalletApi walletApi = supplyWalletApi();
    walletApi.bind(mock(AergoClient.class));
    walletApi.unlock(valid);
    final RawTransaction rawTransaction = RawTransaction.newBuilder()
        .chainIdHash(ChainIdHash.of(BytesValue.of(randomUUID().toString().getBytes())))
        .from(walletApi.getPrincipal())
        .to(randomUUID().toString())
        .amount(Aer.ZERO)
        .nonce(1L)
        .build();

    // then
    assertNotNull(walletApi.sign(rawTransaction));
  }

  @Test
  public void shouldSignFailOnDifferentSender() {
    // when
    final WalletApi walletApi = supplyWalletApi();
    walletApi.bind(mock(AergoClient.class));
    walletApi.unlock(valid);
    final AergoKey newOne = new AergoKeyGenerator().create();
    final RawTransaction rawTransaction = RawTransaction.newBuilder()
        .chainIdHash(ChainIdHash.of(BytesValue.of(randomUUID().toString().getBytes())))
        .from(newOne.getAddress())
        .to(randomUUID().toString())
        .amount(Aer.ZERO)
        .nonce(1L)
        .build();

    // then
    try {
      walletApi.sign(rawTransaction);
      fail();
    } catch (Exception e) {
      // good we expected this
    }
  }

  @Test
  public void shouldSignThrowErrorOnLockedOne() {
    // when
    final WalletApi walletApi = supplyWalletApi();
    walletApi.bind(mock(AergoClient.class));
    final RawTransaction rawTransaction = RawTransaction.newBuilder()
        .chainIdHash(ChainIdHash.of(BytesValue.of(randomUUID().toString().getBytes())))
        .from(storedKey.getAddress())
        .to(randomUUID().toString())
        .amount(Aer.ZERO)
        .nonce(1L)
        .build();

    // then
    try {
      walletApi.sign(rawTransaction);
      fail();
    } catch (HerajException e) {
      // good we expected this
    }
  }

  @Test
  public void testSignMessageOnPlaintext() {
    // when
    final WalletApi walletApi = supplyWalletApi();
    walletApi.bind(mock(AergoClient.class));
    walletApi.unlock(valid);

    // then
    final Signature signature = walletApi
        .signMessage(BytesValue.of(randomUUID().toString().getBytes()));
    assertNotNull(signature);
  }

  @Test
  public void testSignMessageOnHash() {
    // when
    final WalletApi walletApi = supplyWalletApi();
    walletApi.bind(mock(AergoClient.class));
    walletApi.unlock(valid);

    // then
    final byte[] digested = Sha256Utils.digest(randomUUID().toString().getBytes());
    final Signature signature = walletApi.signMessage(Hash.of(BytesValue.of(digested)));
    assertNotNull(signature);
  }

}
