/*
 * @copyright defined in LICENSE.txt
 */

package hera.util.pki;

import static hera.util.IoUtils.stream;
import static java.security.Security.addProvider;
import static org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec;
import static org.slf4j.LoggerFactory.getLogger;

import hera.util.HexUtils;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.slf4j.Logger;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class ECDSAKey {

  protected static final String KEY_ALGORITHM = "ECDSA";

  protected static final String CURVE_NAME = "secp256k1";

  protected static final ECNamedCurveParameterSpec ecSpec;

  @Getter
  protected static final ECDomainParameters ecParams;

  static {
    addProvider(new BouncyCastleProvider());
    ecSpec = getParameterSpec(CURVE_NAME);
    ecParams = new ECDomainParameters(ecSpec.getCurve(), ecSpec.getG(), ecSpec.getN(),
        ecSpec.getH(), ecSpec.getSeed());
  }

  protected final transient Logger logger = getLogger(getClass());

  /**
   * Create ECDSAKey with a private key.
   *
   * @param rawPrivatekey a private key
   * @return {@link ECDSAKey}
   *
   * @throws Exception on failure of creation
   */
  public static ECDSAKey of(final byte[] rawPrivatekey) throws Exception {
    return new ECDSAKey(rawPrivatekey);
  }

  /**
   * Create ECDSAKey with keypair.
   *
   * @param privatekey a private key
   * @param publicKey a public key
   * @return {@link ECDSAKey}
   */
  public static ECDSAKey of(final PrivateKey privatekey, final PublicKey publicKey) {
    return new ECDSAKey(privatekey, publicKey);
  }

  /**
   * ECDSAKey constructor.
   *
   * @param rawPrivatekey raw private key
   *
   * @throws Exception on failure
   */
  public ECDSAKey(final byte[] rawPrivatekey) throws Exception {
    final KeyFactory factory = KeyFactory.getInstance(KEY_ALGORITHM);

    final BigInteger d = new BigInteger(1, rawPrivatekey);
    final ECPrivateKeySpec spec = new ECPrivateKeySpec(d, ecSpec);
    this.privateKey = factory.generatePrivate(spec);

    final ECPoint Q = ecParams.getG()
        .multiply(((org.bouncycastle.jce.interfaces.ECPrivateKey) privateKey).getD());
    final ECPublicKeySpec ecPublicKeySpec = new ECPublicKeySpec(Q, ecSpec);
    this.publicKey = factory.generatePublic(ecPublicKeySpec);
  }

  @Getter
  protected final PrivateKey privateKey;

  @Getter
  protected final PublicKey publicKey;

  /**
   * Sign to plain text.
   *
   * @param plainText text to sign to
   *
   * @return signature ECDSA signature
   */
  public ECDSASignature sign(final InputStream plainText) {
    try {
      final byte[] message = toByteArray(plainText);
      final ECDSASignature signature =
          sign(((org.bouncycastle.jce.interfaces.ECPrivateKey) privateKey).getD(), message);
      logger.trace("ECDSASignature signature: {}", signature);
      return signature;
    } catch (final Throwable e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Generate a deterministic ECDSA signature according to RFC6979.
   *
   * @see <a href="https://github.com/btcsuite/btcd/blob/master/btcec/signature.go">signRFC6979
   *      function in signature.go</a>
   *
   * @param d private key d value
   * @param message message to sign
   * @return ECDSASignature result
   * @throws Exception when sign error occurred
   */
  protected ECDSASignature sign(final BigInteger d, final byte[] message) throws Exception {
    logger.trace("D: {}", d);
    logger.trace("Message in hexa: {}", HexUtils.encode(message));

    final ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
    final ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(d, ecParams);
    signer.init(true, privKey);
    final BigInteger[] components = signer.generateSignature(message);

    final BigInteger r = components[0];
    final BigInteger s = ecParams.getN().subtract(components[1]);

    return new ECDSASignature(r, s);
  }

  /**
   * Check if {@code signature} is valid for {@code plainText}.
   *
   * @param plainText plain text
   * @param signature ECDSA signature
   *
   * @return if valid
   */
  public boolean verify(final InputStream plainText, final ECDSASignature signature) {
    try {
      return verify(getPublicKey(), toByteArray(plainText), signature);
    } catch (final Throwable e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Verify signature with a message and public key x and y point value.
   *
   * @see <a href="https://github.com/golang/go/blob/master/src/crypto/ecdsa/ecdsa.go">verify
   *      function in ecdsa.go</a>
   *
   * @param publicKey a publicKey
   * @param message a message
   * @param signature ECDSA signature
   *
   * @return verification result
   */
  protected boolean verify(final PublicKey publicKey, final byte[] message,
      final ECDSASignature signature) {
    final org.bouncycastle.jce.interfaces.ECPublicKey ecPublicKey =
        (org.bouncycastle.jce.interfaces.ECPublicKey) publicKey;
    final ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
    final ECPublicKeyParameters privKey = new ECPublicKeyParameters(ecPublicKey.getQ(), ecParams);
    signer.init(false, privKey);
    return signer.verifySignature(message, signature.getR(), signature.getS());
  }

  @Override
  public String toString() {
    final org.bouncycastle.jce.interfaces.ECPrivateKey ecPrivateKey =
        (org.bouncycastle.jce.interfaces.ECPrivateKey) privateKey;
    final org.bouncycastle.jce.interfaces.ECPublicKey ecPublicKey =
        (org.bouncycastle.jce.interfaces.ECPublicKey) publicKey;
    return String.format("%s\n%s", ecPrivateKey.toString(), ecPublicKey.toString());
  }

  protected byte[] toByteArray(final InputStream plainText) throws Exception {
    final ByteArrayOutputStream os = new ByteArrayOutputStream();
    stream(plainText, (bytes, offset, length) -> os.write(bytes));
    return os.toByteArray();
  }

}
