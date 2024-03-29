package security.elgamal;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.List;


import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;

import security.generic.CipherConstants;
import security.generic.NTL;

// Reference
// https://github.com/dlitz/pycrypto/blob/master/lib/Crypto/PublicKey/ElGamal.py
public class ElGamalCipher extends CipherSpi
{
	private static final boolean ADDITIVE = false;
	
	protected int stateMode;
	protected Key keyElGamal;
	protected SecureRandom SECURE_RANDOM;
	protected int plaintextSize;
	protected int ciphertextSize;
	
	protected byte [] hrgm;
	protected byte [] gr;
	protected byte [] plaintext;
	
	/**
	 * This class support no modes, so engineSetMode() throw exception when
	 * called.
	 */
	protected final void engineSetMode(String mode)
			throws NoSuchAlgorithmException 
	{
		throw new NoSuchAlgorithmException("Paillier supports no modes.");
	}

	/**
	 * This class support no padding, so engineSetPadding() throw exception when
	 * called.
	 */
	protected final void engineSetPadding(String padding)
			throws NoSuchPaddingException 
	{
		throw new NoSuchPaddingException("Paillier supports no padding.");
	}

	/**
	 * Perform actual encryption ,creates single array and updates the result
	 * after the encryption.
	 * 
	 * @param input
	 *            - the input in bytes
	 * @param inputOffset
	 *            - the offset in input where the input starts always zero
	 * @param inputLenth
	 *            - the input length
	 * @param output
	 *            - the buffer for the result
	 * @param outputOffset
	 *            - the offset in output where the result is stored
	 * @return the number of bytes stored in output
	 * @throws Exception
	 *             throws if Plaintext m is not in Z_n , m should be less then n
	 */
	protected final int encrypt(byte[] input, int inputOffset, int inputLenth,
			byte[] output, int outputOffset) throws Exception
	{
		BigInteger m = new BigInteger(input);

		// get the public key in order to encrypt
		ElGamal_Ciphertext c = encrypt((ElGamalPublicKey) keyElGamal, m);
		
		// Convert to bytes!
		gr = c.gr.toByteArray();
		hrgm = c.hrgm.toByteArray();
		// MAX 129 each size
		System.arraycopy(gr, 0, output, ciphertextSize - ciphertextSize/2 - gr.length, gr.length);
		System.arraycopy(hrgm, 0, output, ciphertextSize - hrgm.length, hrgm.length);
		
		plaintextSize = input.length;
		ciphertextSize = gr.length + hrgm.length;
		return ciphertextSize;
	}

	/**
	 * Perform actual decryption ,creates single array for the output and updates
	 * the result after the decryption.
	 * 
	 * @param input
	 *            - the input in bytes
	 * @param inputOffset
	 *            - the offset in input where the input starts always zero
	 * @param inputLenth
	 *            - the input length
	 * @param output
	 *            - the buffer for the result
	 * @param outputOffset
	 *            - the offset in output where the result is stored
	 * @return the number of bytes stored in output
	 */
	protected final int decrypt(byte[] input, int inputOffset, int inputLenth,
			byte[] output, int outputOffset)
	{
		// extract gr and hrgm
		BigInteger gr = null;
		BigInteger hrgm = null;
		gr = new BigInteger(Arrays.copyOfRange(input, 0, 129));
		hrgm = new BigInteger(Arrays.copyOfRange(input, 129, input.length));
		
		// calculate the message
		byte [] messageBytes = decrypt((ElGamalPrivateKey) keyElGamal, new ElGamal_Ciphertext(gr, hrgm)).toByteArray();
		int gatedLength = Math.min(messageBytes.length, plaintextSize);
		System.arraycopy(messageBytes, 0, output, plaintextSize - gatedLength, gatedLength);
		return plaintextSize;
	}

	/**
	 * PaillierHomomorphicCipher doesn't recognise any algorithm - specific initialisations
	 * so the algorithm specific engineInit() just calls the previous overloaded
	 * version of engineInit()
	 * 
	 * @param opmode
	 *            -cipher mode
	 * @param key
	 *            - Key
	 * @param params
	 *            - AlgorithmParameterSpec
	 * @see javax.crypto.CipherSpi#engineInit(int, java.security.Key,
	 *      java.security.spec.AlgorithmParameterSpec,
	 *      java.security.SecureRandom)
	 */

	protected final void engineInit(int opmode, Key key,
			AlgorithmParameterSpec params, SecureRandom random)
			throws InvalidKeyException, InvalidAlgorithmParameterException
	{
		engineInit(opmode, key, random);
	}

	protected final void engineInit(int opmode, Key key, AlgorithmParameters params,
			SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException 
	{
		engineInit(opmode, key, random);
	}

	/**
	 * Calls the second overloaded version of the same method.
	 * 
	 * @return the result from encryption or decryption
	 */
	protected final byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) 
	{
		byte[] out = new byte[engineGetOutputSize(inputLen)];
		try 
		{
			engineUpdate(input, inputOffset, inputLen, out, 0);
		} 
		catch (ShortBufferException sbe) 
		{

		}
		return out;
	}

	/**
	 * Creates a single input array from the buffered data and supplied input
	 * data. Calculates the location and the length of the last fractional block
	 * in the input data. Transforms all full blocks in the input data. Save the
	 * last fractional block in the internal buffer.
	 * 
	 * @param input
	 *            - the input in bytes
	 * @param inputOffset
	 *            - the offset in input where the input starts always zero
	 * @param inputLen
	 *            - the input length
	 * @param output
	 *            - the buffer for the result
	 * @param outputOffset
	 *            - the offset in output where the result is stored
	 * @return the number of bytes stored in output
	 */
	protected final int engineUpdate(byte[] input, int inputOffset, int inputLen,
			byte[] output, int outputOffset) throws ShortBufferException 
	{
		int size;
		if (stateMode == Cipher.ENCRYPT_MODE)
		{
			try
			{
				size = encrypt(input, inputOffset, inputLen, output, outputOffset);
				return size;
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
		}
		else if (stateMode == Cipher.DECRYPT_MODE)
		{
			size = decrypt(input, inputOffset, inputLen, output, outputOffset);
			return size;
		}
		return 0;
	}

	/**
	 * Calls the second overloaded version of the same method,
	 * to perform the required operation based on the state of the cipher.
	 * 
	 * @return returns the result from encryption or decryption
	 */
	protected final byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
			throws IllegalBlockSizeException, BadPaddingException
	{
		byte[] out = new byte[engineGetOutputSize(inputLen)];
		try 
		{
			engineDoFinal(input, inputOffset, inputLen, out, 0);
		} 
		catch (ShortBufferException sbe)
		{
			
		}
		return out;
	}

	/**
	 * Calls encrypt or decrypt based on the state of the cipher. Creates a
	 * single input array from the supplied input data. And returns number of
	 * bytes stored in output.
	 * 
	 * @param input
	 *            - the input buffer
	 * @param inputOffset
	 *            - the offset in input where the input starts always zero
	 * @param inputLen
	 *            - the input length
	 * @param output
	 *            - the buffer for the result
	 * @param outputOffset
	 *            - the offset in output where the result is stored
	 * @return the number of bytes stored in output
	 */
	protected final int engineDoFinal(byte[] input, int inputOffset, int inputLen,
			byte[] output, int outputOffset)
					throws ShortBufferException, IllegalBlockSizeException, BadPaddingException
	{
		// Create a single array of input data
		byte[] totalInput = new byte[inputLen];
		if (inputLen > 0)
		{
			System.arraycopy(input, inputOffset, totalInput, 0, inputLen);
		}
		if (stateMode == Cipher.ENCRYPT_MODE)
		{
			try 
			{
				return encrypt(input, inputOffset, inputLen, output, outputOffset);
			}
			catch (Exception e) 
			{
				e.printStackTrace();
			}
		}
		else if (stateMode == Cipher.DECRYPT_MODE)
		{
			return decrypt(input, inputOffset, inputLen, output, outputOffset);
		}
		return 0;
	}

	/**
	 * This method returns the appropriate block size , based on cipher.
	 * 
	 * @return BlockSize - the block size(in bytes).
	 */
	protected final int engineGetBlockSize() 
	{
		if (stateMode == Cipher.DECRYPT_MODE)
		{
			return ciphertextSize;
		}
		else
		{
			return plaintextSize;
		}
	}

	/**
	 * This method returns null.
	 */
	protected final byte[] engineGetIV()
	{
		return null;
	}

	protected final AlgorithmParameters engineGetParameters() 
	{
		return null;
	}

	/**
	 * Initialises this cipher with key and a source of randomness
	 */
	protected final void engineInit(int mode, Key key, SecureRandom random)
			throws InvalidKeyException 
	{
		if (mode == Cipher.ENCRYPT_MODE)
		{
			if (!(key instanceof ElGamalPublicKey))
			{
				throw new InvalidKeyException("I didn't get a ElGamalPublicKey!");
			}
		}
		else if (mode == Cipher.DECRYPT_MODE)
		{
			if (!(key instanceof ElGamalPrivateKey))
			{
				throw new InvalidKeyException("I didn't get a ElGamalPrivateKey!");
			}
		}		
		else
		{
			throw new IllegalArgumentException("Bad mode: " + mode);
		}
		this.stateMode = mode;
		this.keyElGamal = key;
		this.SECURE_RANDOM = random;
		int modulusLength = ((ElGamal_Key) key).getP().bitLength();
		calculateBlockSizes(modulusLength);
	}

	// --------------------------Relevant ElGamal---------------------------------------
	public static ElGamal_Ciphertext encrypt(ElGamalPublicKey key, BigInteger message)
	{
		if(ADDITIVE)
		{
			return Encrypt_Homomorph(key, message);
		}
		else
		{
			return Encrypt(key, message);
		}
	}
	
	public static ElGamal_Ciphertext encrypt(ElGamalPublicKey key, long m)
	{
		BigInteger message = BigInteger.valueOf(m);
		return encrypt(key, message);
	}
	
	public static BigInteger decrypt(ElGamalPrivateKey key, ElGamal_Ciphertext gr_mhr)
	{
		if(ADDITIVE)
		{
			return Decrypt_Homomorph(key, gr_mhr);	
		}
		else
		{
			return Decrypt(key, gr_mhr);	
		}
	}
	
	/*
	 * @param (p,g,h) public key
	 * @param message message	
	 */
	private static ElGamal_Ciphertext Encrypt(ElGamalPublicKey Key, BigInteger message)
	{
		BigInteger pPrime = Key.p.subtract(BigInteger.ONE).divide(ElGamalKeyPairGenerator.TWO);
		BigInteger r = NTL.RandomBnd(pPrime);
		BigInteger gr = Key.g.modPow(r, Key.p);
		BigInteger hrgm = message.multiply(Key.h.modPow(r, Key.p)).mod(Key.p);
		// encrypt couple (g^r (mod p), m * h^r (mod p))
		return new ElGamal_Ciphertext(gr, hrgm);
	}

	/*
	 * Encrypt ElGamal homomorphic
	 *
	 * @param (p, g, h) public key
	 * @param message message
	 */
	private static ElGamal_Ciphertext Encrypt_Homomorph(ElGamalPublicKey key, BigInteger message) 
	{
		BigInteger pPrime = key.p.subtract(BigInteger.ONE).divide(ElGamalKeyPairGenerator.TWO);
		BigInteger r = NTL.RandomBnd(pPrime);
		// encrypt couple (g^r (mod p), h^r * g^m (mod p))
		BigInteger hr = key.h.modPow(r, key.p);
		BigInteger gm = key.g.modPow(message, key.p);
		return new ElGamal_Ciphertext(key.g.modPow(r, key.p), hr.multiply(gm).mod(key.p));
	}
	
	/*
	 * Decrypt ElGamal
	 *
	 * @param (p, x) secret key
	 * @param (gr, mhr) = (g^r, m * h^r)
	 * @return the decrypted message
	 */
	private static BigInteger Decrypt(ElGamalPrivateKey key, ElGamal_Ciphertext c)
	{
		BigInteger hr = c.gr.modPow(key.x, key.p);
		return c.hrgm.multiply(hr.modInverse(key.p)).mod(key.p);
	}

	/*
	 * @param (p, x) secret key
	 * @param (gr, mhr) = (g^r, h^r * g^m)
	 * @return the decrypted message
	 */
	private static BigInteger Decrypt_Homomorph(ElGamalPrivateKey key, ElGamal_Ciphertext c) 
	{
		// h^r (mod p) = g^{r * x} (mod p)
		BigInteger hr = c.gr.modPow(key.x, key.p);
		// g^m = (h^r * g^m) * (h^r)-1 (mod p) = g^m (mod p)
		BigInteger gm = c.hrgm.multiply(hr.modInverse(key.p)).mod(key.p);
		BigInteger m = key.LUT.get(gm);
		
		if (m != null)
		{
			// If I get this, there is a chance I might have a negative number to make?
			if (m.compareTo(CipherConstants.FIELD_SIZE) == 1)
			{
				m = m.mod(key.p.subtract(BigInteger.ONE));
			}
			return m;
		}
		else
		{
			throw new IllegalArgumentException("Entry not found!");
		}
	}
	
	// --------------Additively Homomorphic Operations---------------------------
	
    // On input an encrypted value x and a scalar c
	// IF ADDITIVE returns an encryption of cx.
	// IF MULTIPLICATIVE r
    public static ElGamal_Ciphertext multiply(ElGamal_Ciphertext ciphertext1, BigInteger scalar, ElGamalPublicKey pk)
    {
		ElGamal_Ciphertext answer = null;
    	if(ADDITIVE)
    	{
        	answer = new ElGamal_Ciphertext(ciphertext1.gr.modPow(scalar, pk.p), ciphertext1.hrgm.modPow(scalar, pk.p));
    	}
    	else
    	{
    		// THROW ERROR?
    		answer = new ElGamal_Ciphertext(ciphertext1.gr.modPow(scalar, pk.p), ciphertext1.hrgm.modPow(scalar, pk.p));
    	}
        return answer;
    }
    
	public static ElGamal_Ciphertext multiply(ElGamal_Ciphertext ciphertext, long scalar, ElGamalPublicKey e_pk) 
	{
		return multiply(ciphertext, BigInteger.valueOf(scalar), e_pk);
	}
    
    // On input two encrypted values, returns an encryption of the sum of the values
    // Input is (<gr_1, mhr_1>, <gr_2, mhr_2>) --> (g^r, g^m * h^r)
    // Output is (gr_1 * gr_2, mhr_1 * mhr_2)
    public static ElGamal_Ciphertext add(ElGamal_Ciphertext ciphertext1, ElGamal_Ciphertext ciphertext2, ElGamalPublicKey pk)
    {
		ElGamal_Ciphertext answer = null;
		if (ADDITIVE)
		{
			answer = new ElGamal_Ciphertext(ciphertext1.gr.multiply(ciphertext2.gr).mod(pk.p), 
				ciphertext1.hrgm.multiply(ciphertext2.hrgm).mod(pk.p));
		}
		else
		{
			// NOW YOU ARE GETTING THE PRODUCT NOT SUM OF CIPHER TEXT!
			answer = new ElGamal_Ciphertext(ciphertext1.gr.multiply(ciphertext2.gr).mod(pk.p), 
					ciphertext1.hrgm.multiply(ciphertext2.hrgm).mod(pk.p));
		}
		return answer;
    }
    
    public static ElGamal_Ciphertext subtract(ElGamal_Ciphertext ciphertext1, ElGamal_Ciphertext ciphertext2, ElGamalPublicKey pk)
    {
    	ElGamal_Ciphertext neg_ciphertext2 = null;
    	ElGamal_Ciphertext ciphertext = null;
    	if(ADDITIVE)
    	{
    		neg_ciphertext2 = ElGamalCipher.multiply(ciphertext2, -1, pk);
    		ciphertext = ElGamalCipher.add(ciphertext1, neg_ciphertext2, pk);
    	}
    	else
    	{
    		neg_ciphertext2 = ElGamalCipher.multiply(ciphertext2, -1, pk);
    		ciphertext = ElGamalCipher.add(ciphertext1, neg_ciphertext2, pk);
    	}
    	return ciphertext;
    }
	
	public static ElGamal_Ciphertext sum(List<ElGamal_Ciphertext> values, ElGamalPublicKey pk, int limit)
	{
		ElGamal_Ciphertext sum = ElGamalCipher.encrypt(pk, BigInteger.ZERO);
		if (limit <= 0)
		{
			return sum;
		}
		else if(limit > values.size())
		{
			for (int i = 0; i < values.size(); i++)
			{
				sum = ElGamalCipher.add(sum, values.get(i), pk);
			}
		}
		else
		{
			for (int i = 0; i < limit; i++)
			{
				sum = ElGamalCipher.add(sum, values.get(i), pk);
			}
		}
		return sum;
	}
	
	public static ElGamal_Ciphertext sum(ElGamal_Ciphertext [] values, ElGamalPublicKey pk, int limit)
	{
		ElGamal_Ciphertext sum = ElGamalCipher.encrypt(pk, BigInteger.ZERO);
		if (limit <= 0)
		{
			return sum;
		}
		else if(limit > values.length)
		{
			for (int i = 0; i < values.length; i++)
			{
				sum = ElGamalCipher.add(sum, values[i], pk);
			}
		}
		else
		{
			for (int i = 0; i < limit; i++)
			{
				sum = ElGamalCipher.add(sum, values[i], pk);
			}
		}
		return sum;
	}
	
	public static ElGamal_Ciphertext sum_product (ElGamalPublicKey pk, List<ElGamal_Ciphertext> cipher, List<Long> plain)
	{
		if(cipher.size() != plain.size())
		{
			throw new IllegalArgumentException("Arrays are NOT the same size!");
		}
		
		ElGamal_Ciphertext [] product_vector = new ElGamal_Ciphertext[cipher.size()];
		for (int i = 0; i < product_vector.length; i++)
		{
			product_vector[i] = ElGamalCipher.multiply(cipher.get(i), plain.get(i), pk);
		}
		return ElGamalCipher.sum(product_vector, pk, product_vector.length);
	}
	
	public static ElGamal_Ciphertext sum_product (ElGamalPublicKey pk, List<ElGamal_Ciphertext> cipher, Long [] plain)
	{
		if(cipher.size() != plain.length)
		{
			throw new IllegalArgumentException("Arrays are NOT the same size!");
		}
		
		ElGamal_Ciphertext [] product_vector = new ElGamal_Ciphertext[cipher.size()];
		for (int i = 0; i < product_vector.length; i++)
		{
			product_vector[i] = ElGamalCipher.multiply(cipher.get(i), plain[i], pk);
		}
		return ElGamalCipher.sum(product_vector, pk, product_vector.length);
	}
	// ------------------------PUBLIC FACING METHODS---------------------------------------------------
	public void init(int encryptMode, ElGamalPublicKey pk) 
			throws InvalidKeyException, InvalidAlgorithmParameterException
	{
		engineInit(encryptMode, pk, new SecureRandom());
	}

	public void init(int decryptMode, ElGamalPrivateKey sk)
			throws InvalidKeyException, InvalidAlgorithmParameterException 
	{
		engineInit(decryptMode, sk, new SecureRandom());
	}
	
	public byte[] doFinal(byte[] bytes) 
			throws BadPaddingException, IllegalBlockSizeException 
	{
		byte [] answer = engineDoFinal(bytes, 0, bytes.length);
		return answer;
	}

	protected int engineGetOutputSize(int inputLen) 
	{
		if (stateMode == Cipher.ENCRYPT_MODE) 
		{
			return ciphertextSize;
		} 
		else 
		{
			return plaintextSize;
		}
	}
	
	protected final void calculateBlockSizes(int modulusLength)
	{
		plaintextSize = ((modulusLength + 8) / 8);
		ciphertextSize = (((modulusLength + 12) / 8) * 2);
	}
}
