/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package remoteprovisioning.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import COSE.AlgorithmID;
import COSE.Attribute;
import COSE.HeaderKeys;
import COSE.KeyKeys;
import COSE.OneKey;
import COSE.Sign1Message;
import com.upokecenter.cbor.CBORObject;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import remoteprovisioning.*;

/**
 * Testing class designed to cover functionality in the CertificateRequestSerializer/Deserializer
 * code.
 */
@RunWith(JUnit4.class)
public class CertificateRequestDeserializerTest {
  private final byte[] mac = {
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
    27, 28, 29, 30, 31, 32
  };

  private final byte[] challenge = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
  private byte[] certificateRequestSerialized;
  private byte[] certificateRequestSerializedP256;

  private CBORObject deviceInfo;

  private OneKey deviceKeyPair;
  private OneKey deviceKeyPairP256;

  private OneKey[] keysToSign;

  private Sign1Message[] bcc;
  private Sign1Message[] bccP256;

  private KeyPair serverKeyPairP256;

  private AsymmetricCipherKeyPair serverKeyPair;
  private OneKey oemKeyPair;
  private Sign1Message[] additionalDkSignatureChain;

  private CertificateRequestDeserializer certRequest;
  private CertificateRequestDeserializer certRequestP256;

  @Before
  public void setUp() throws Exception {
    Security.addProvider(new EdDSASecurityProvider());
    deviceInfo = CBORObject.NewArray();
    CBORObject deviceInfoVerified = CBORObject.NewMap();
    deviceInfoVerified.Add("board", "devboard");
    deviceInfoVerified.Add("manufacturer", "devmanufacturer");
    deviceInfoVerified.Add("version", 1);

    CBORObject deviceInfoUnverified = CBORObject.NewMap();
    deviceInfoUnverified.Add("fingerprint", "cool/new/device/thing");

    deviceInfo.Add(deviceInfoVerified);
    deviceInfo.Add(deviceInfoUnverified);

    deviceKeyPair = OneKey.generateKey(KeyKeys.OKP_Ed25519);
    deviceKeyPair.add(KeyKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR());

    deviceKeyPairP256 = OneKey.generateKey(KeyKeys.EC2_P256);
    deviceKeyPairP256.add(KeyKeys.Algorithm, AlgorithmID.ECDSA_256.AsCBOR());

    OneKey keyToSign = OneKey.generateKey(KeyKeys.EC2_P256).PublicKey();
    keyToSign.add(KeyKeys.Algorithm, AlgorithmID.ECDSA_256.AsCBOR());
    keysToSign = new OneKey[] {keyToSign};

    // Generate a BCC and self sign
    Sign1Message bccCert = new Sign1Message();
    bccCert.addAttribute(HeaderKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR(), Attribute.PROTECTED);
    bccCert.SetContent(deviceKeyPair.PublicKey().EncodeToBytes());
    bccCert.sign(deviceKeyPair);
    bcc = new Sign1Message[] {bccCert};

    // Generate a BCC and self sign
    Sign1Message bccCertP256 = new Sign1Message();
    bccCertP256.addAttribute(
        HeaderKeys.Algorithm, AlgorithmID.ECDSA_256.AsCBOR(), Attribute.PROTECTED);
    bccCertP256.SetContent(deviceKeyPairP256.PublicKey().EncodeToBytes());
    bccCertP256.sign(deviceKeyPairP256);
    bccP256 = new Sign1Message[] {bccCertP256};

    // Generate the EEK server key pair
    serverKeyPair = CryptoUtil.genX25519();
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
    kpg.initialize(new ECGenParameterSpec("secp256r1"));
    serverKeyPairP256 = kpg.genKeyPair();

    // Generate the additional device key signing certificates.
    // OEM certificate is self signed; device certificate is signed by the OEM key pair
    oemKeyPair = OneKey.generateKey(KeyKeys.OKP_Ed25519);
    oemKeyPair.add(KeyKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR());
    Sign1Message signingCert = new Sign1Message();
    Sign1Message deviceCert = new Sign1Message();
    signingCert.addAttribute(HeaderKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR(), Attribute.PROTECTED);
    signingCert.SetContent(oemKeyPair.PublicKey().EncodeToBytes());
    signingCert.sign(oemKeyPair);

    deviceCert.addAttribute(HeaderKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR(), Attribute.PROTECTED);
    deviceCert.SetContent(deviceKeyPair.PublicKey().EncodeToBytes());
    deviceCert.sign(oemKeyPair);
    additionalDkSignatureChain = new Sign1Message[] {signingCert, deviceCert};

    // Build the CBOR blob
    certificateRequestSerialized =
        new CertificateRequestSerializer.Builder(
                (X25519PublicKeyParameters) serverKeyPair.getPublic())
            .setDeviceInfo(deviceInfo)
            .setPublicKeys(keysToSign)
            .setMacKey(mac)
            .setChallenge(challenge)
            .setBcc(bcc, deviceKeyPair.PublicKey().AsCBOR())
            .setDkPriv(deviceKeyPair)
            .addAdditionalDkSignature("fake" /* signerId */, additionalDkSignatureChain)
            .build()
            .buildCertificateRequest();
    certRequest = new CertificateRequestDeserializer(certificateRequestSerialized);

    certificateRequestSerializedP256 =
        new CertificateRequestSerializer.Builder((ECPublicKey) serverKeyPairP256.getPublic())
            .setDeviceInfo(deviceInfo)
            .setPublicKeys(keysToSign)
            .setMacKey(mac)
            .setChallenge(challenge)
            .setBcc(bccP256, deviceKeyPairP256.PublicKey().AsCBOR())
            .setDkPriv(deviceKeyPairP256)
            .build()
            .buildCertificateRequest();
    certRequestP256 = new CertificateRequestDeserializer(certificateRequestSerializedP256);
  }

  @Test
  public void testSerializeDeserialize() throws Exception {
    ProtectedDataPayload payload =
        new ProtectedDataPayload(
            certRequest.getProtectedData(),
            certRequest.getChallenge(),
            certRequest.getDeviceInfoEncoded(),
            certRequest.getMacedKeysMac(),
            serverKeyPair);
    assertNotNull(payload);
    assertTrue(
        Arrays.equals(
            payload.getDevicePublicKey(),
            deviceKeyPair.PublicKey().get(KeyKeys.OKP_X).ToObject(byte[].class)));
    assertTrue(Arrays.equals(payload.getMacKey(), mac));

    ArrayList<PublicKey> publicKeys =
        CertificateRequestDeserializer.retrievePublicKeys(
            certRequest.getMacedKeysToSign(), payload.getMacKey());
    assertNotNull(publicKeys);
    assertEquals(1, publicKeys.size());
    assertTrue(
        Arrays.equals(
            publicKeys.get(0).getEncoded(),
            CryptoUtil.oneKeyToP256PublicKey(keysToSign[0]).getEncoded()));
  }

  @Test
  public void testSerializeDeserializeP256() throws Exception {
    ProtectedDataPayload payload =
        new ProtectedDataPayload(
            certRequestP256.getProtectedData(),
            certRequestP256.getChallenge(),
            certRequestP256.getDeviceInfoEncoded(),
            certRequestP256.getMacedKeysMac(),
            serverKeyPairP256);
    assertNotNull(payload);

    assertTrue(
        Arrays.equals(
            payload.getDevicePublicKey(),
            CryptoUtil.p256PubKeyToBytes((ECPublicKey) deviceKeyPairP256.AsPublicKey())));
    assertTrue(Arrays.equals(payload.getMacKey(), mac));

    ArrayList<PublicKey> publicKeys =
        CertificateRequestDeserializer.retrievePublicKeys(
            certRequest.getMacedKeysToSign(), payload.getMacKey());
    assertNotNull(publicKeys);
    assertEquals(1, publicKeys.size());
    assertTrue(
        Arrays.equals(
            publicKeys.get(0).getEncoded(),
            CryptoUtil.oneKeyToP256PublicKey(keysToSign[0]).getEncoded()));
  }
  @Test
  public void testWrongMacFails() throws Exception {
    byte[] badMac = Arrays.copyOf(mac, mac.length);
    badMac[4] = 21;
    try {
      CertificateRequestDeserializer.retrievePublicKeys(certRequest.getMacedKeysToSign(), badMac);
    } catch (CryptoException e) {
      assertEquals(CryptoException.PUBLIC_KEYS_MAC_VERIFICATION_FAILED, e.getErrorCode());
      return;
    }
    fail();
  }

  @Test
  public void testWrongEekFails() throws Exception {
    AsymmetricCipherKeyPair fakeKeyPair = CryptoUtil.genX25519();
    try {
      ProtectedDataPayload payload =
          new ProtectedDataPayload(
              certRequest.getProtectedData(),
              certRequest.getChallenge(),
              certRequest.getDeviceInfoEncoded(),
              certRequest.getMacedKeysMac(),
              fakeKeyPair);
    } catch (CryptoException e) {
      assertEquals(CryptoException.DECRYPTION_FAILURE, e.getErrorCode());
      return;
    }
    fail();
  }

  @Test
  public void testWrongChallengeAadMacFails() throws Exception {
    byte[] badChallenge = Arrays.copyOf(challenge, challenge.length);
    badChallenge[0] = 12;
    try {
      ProtectedDataPayload payload =
          new ProtectedDataPayload(
              certRequest.getProtectedData(),
              badChallenge,
              certRequest.getDeviceInfoEncoded(),
              certRequest.getMacedKeysMac(),
              serverKeyPair);
    } catch (CryptoException e) {
      assertEquals(CryptoException.MAC_WITH_AAD_SIGNATURE_VERIFICATION_FAILED, e.getErrorCode());
      return;
    }
    fail();
  }

  @Test
  public void testWrongDeviceInfoAadMacFails() throws Exception {
    try {
      ProtectedDataPayload payload =
          new ProtectedDataPayload(
              certRequest.getProtectedData(),
              certRequest.getChallenge(),
              new byte[] {-123, 97, 97, 97, 98, 97, 99, 97, 100, 97, 102},
              certRequest.getMacedKeysMac(),
              serverKeyPair);
    } catch (CryptoException e) {
      assertEquals(CryptoException.MAC_WITH_AAD_SIGNATURE_VERIFICATION_FAILED, e.getErrorCode());
      return;
    }
    fail();
  }

  @Test
  public void TestAdditionalSignatureMapEmptyPasses() throws Exception {
    certificateRequestSerialized =
        new CertificateRequestSerializer.Builder(
                (X25519PublicKeyParameters) serverKeyPair.getPublic())
            .setDeviceInfo(deviceInfo)
            .setPublicKeys(keysToSign)
            .setMacKey(mac)
            .setChallenge(challenge)
            .setBcc(bcc, deviceKeyPair.PublicKey().AsCBOR())
            .setDkPriv(deviceKeyPair)
            .build()
            .buildCertificateRequest();
    certRequest = new CertificateRequestDeserializer(certificateRequestSerialized);
    ProtectedDataPayload payload =
        new ProtectedDataPayload(
            certRequest.getProtectedData(),
            challenge,
            certRequest.getDeviceInfoEncoded(),
            certRequest.getMacedKeysMac(),
            serverKeyPair);
    assertNotNull(payload);
  }

  @Test
  public void testAdditionalSignatureBadRootSigFails() throws Exception {
    Sign1Message signingCert = new Sign1Message();
    Sign1Message deviceCert = new Sign1Message();
    signingCert.addAttribute(HeaderKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR(), Attribute.PROTECTED);
    signingCert.SetContent(oemKeyPair.PublicKey().EncodeToBytes());
    // Sign with the wrong key
    signingCert.sign(deviceKeyPair);

    deviceCert.addAttribute(HeaderKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR(), Attribute.PROTECTED);
    deviceCert.SetContent(deviceKeyPair.PublicKey().EncodeToBytes());
    deviceCert.sign(oemKeyPair);
    additionalDkSignatureChain = new Sign1Message[] {signingCert, deviceCert};

    certificateRequestSerialized =
        new CertificateRequestSerializer.Builder(
                (X25519PublicKeyParameters) serverKeyPair.getPublic())
            .setDeviceInfo(deviceInfo)
            .setPublicKeys(keysToSign)
            .setMacKey(mac)
            .setChallenge(challenge)
            .setBcc(bcc, deviceKeyPair.PublicKey().AsCBOR())
            .setDkPriv(deviceKeyPair)
            .addAdditionalDkSignature("fake" /* signerId */, additionalDkSignatureChain)
            .build()
            .buildCertificateRequest();
    certRequest = new CertificateRequestDeserializer(certificateRequestSerialized);
    try {
      ProtectedDataPayload payload =
          new ProtectedDataPayload(
              certRequest.getProtectedData(),
              challenge,
              certRequest.getDeviceInfoEncoded(),
              certRequest.getMacedKeysMac(),
              serverKeyPair);
    } catch (CryptoException e) {
      assertEquals(e.getErrorCode(), CryptoException.VERIFICATION_FAILURE);
      return;
    }
    fail();
  }

  @Test
  public void testAdditionalSignatureBadLeafSigFails() throws Exception {
    Sign1Message signingCert = new Sign1Message();
    Sign1Message deviceCert = new Sign1Message();
    signingCert.addAttribute(HeaderKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR(), Attribute.PROTECTED);
    signingCert.SetContent(oemKeyPair.PublicKey().EncodeToBytes());
    signingCert.sign(oemKeyPair);

    deviceCert.addAttribute(HeaderKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR(), Attribute.PROTECTED);
    deviceCert.SetContent(deviceKeyPair.PublicKey().EncodeToBytes());
    // Sign with the wrong key
    deviceCert.sign(deviceKeyPair);
    additionalDkSignatureChain = new Sign1Message[] {signingCert, deviceCert};

    certificateRequestSerialized =
        new CertificateRequestSerializer.Builder(
                (X25519PublicKeyParameters) serverKeyPair.getPublic())
            .setDeviceInfo(deviceInfo)
            .setPublicKeys(keysToSign)
            .setMacKey(mac)
            .setChallenge(challenge)
            .setBcc(bcc, deviceKeyPair.PublicKey().AsCBOR())
            .setDkPriv(deviceKeyPair)
            .addAdditionalDkSignature("fake" /* signerId */, additionalDkSignatureChain)
            .build()
            .buildCertificateRequest();
    certRequest = new CertificateRequestDeserializer(certificateRequestSerialized);
    try {
      ProtectedDataPayload payload =
          new ProtectedDataPayload(
              certRequest.getProtectedData(),
              challenge,
              certRequest.getDeviceInfoEncoded(),
              certRequest.getMacedKeysMac(),
              serverKeyPair);
    } catch (CryptoException e) {
      assertEquals(e.getErrorCode(), CryptoException.VERIFICATION_FAILURE);
      return;
    }
    fail();
  }

  @Test
  public void testAdditionalSignatureWrongDeviceKeyFails() throws Exception {
    Sign1Message signingCert = new Sign1Message();
    Sign1Message deviceCert = new Sign1Message();
    signingCert.addAttribute(HeaderKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR(), Attribute.PROTECTED);
    signingCert.SetContent(oemKeyPair.PublicKey().EncodeToBytes());
    signingCert.sign(oemKeyPair);

    deviceCert.addAttribute(HeaderKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR(), Attribute.PROTECTED);
    // Sign the wrong key
    deviceCert.SetContent(oemKeyPair.PublicKey().EncodeToBytes());
    deviceCert.sign(oemKeyPair);
    additionalDkSignatureChain = new Sign1Message[] {signingCert, deviceCert};

    certificateRequestSerialized =
        new CertificateRequestSerializer.Builder(
                (X25519PublicKeyParameters) serverKeyPair.getPublic())
            .setDeviceInfo(deviceInfo)
            .setPublicKeys(keysToSign)
            .setMacKey(mac)
            .setChallenge(challenge)
            .setBcc(bcc, deviceKeyPair.PublicKey().AsCBOR())
            .setDkPriv(deviceKeyPair)
            .addAdditionalDkSignature("fake" /* signerId */, additionalDkSignatureChain)
            .build()
            .buildCertificateRequest();
    certRequest = new CertificateRequestDeserializer(certificateRequestSerialized);
    try {
      ProtectedDataPayload payload =
          new ProtectedDataPayload(
              certRequest.getProtectedData(),
              challenge,
              certRequest.getDeviceInfoEncoded(),
              certRequest.getMacedKeysMac(),
              serverKeyPair);
    } catch (CryptoException e) {
      assertEquals(e.getErrorCode(), CryptoException.VERIFICATION_FAILURE);
      return;
    }
    fail();
  }
}
