package security.elgamal;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.SignatureSpi;
import java.util.Arrays;

import security.generic.CipherConstants;
import security.generic.NTL;

public class ElGamalSignature extends SignatureSpi implements CipherConstants
{
	private ElGamalPrivateKey sk;
	private ElGamalPublicKey pk;
	private boolean VERIFY_MODE;
	private byte [] encoded_hash;
	
	protected void engineInitVerify(PublicKey publicKey) 
			throws InvalidKeyException 
	{
		if(!(publicKey instanceof ElGamalPublicKey))
		{
			throw new InvalidKeyException("Didn't receive DGK Public Key!");
		}
		pk = (ElGamalPublicKey) publicKey;
		sk = null;
		VERIFY_MODE = true;
	}

	protected void engineInitSign(PrivateKey privateKey) 
			throws InvalidKeyException 
	{
		if(!(privateKey instanceof ElGamalPrivateKey))
		{
			throw new InvalidKeyException("Didn't receive ElGamal Private Key!");
		}
		pk = null;
		sk = (ElGamalPrivateKey) privateKey;
		VERIFY_MODE = false;
	}

	// Input 1:
	protected void engineUpdate(byte b) 
			throws SignatureException 
	{
		// Since I am using SHA-256, that is 256 bits or 32 bytes long!
	}

	// Input 2: Prepare bytes to sign or verify!
	protected void engineUpdate(byte [] b, int off, int len) 
			throws SignatureException 
	{
		// Since I am using SHA-256, that is 256 bits or 32 bytes long!
		MessageDigest digest = null;
		try
		{
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
		this.encoded_hash = digest.digest(b);
	}
	
	// https://en.wikipedia.org/wiki/ElGamal_signature_scheme
	protected byte[] engineSign()
			throws SignatureException 
	{
		byte [] signature = null;
		byte [] r = null;
		byte [] s = null;
		if(VERIFY_MODE)
		{
			throw new SignatureException("Did not Initialize SignInit!");
		}
		else
		{
			ElGamal_Ciphertext sigma = sign(new BigInteger(encoded_hash));
			r = sigma.getA().toByteArray();
			s = sigma.getB().toByteArray();
			// Concat both BigIntegers!
			signature = new byte[r.length + s.length];
			System.arraycopy(r, 0, signature, 0, r.length);
			System.arraycopy(s, 0, signature, r.length, s.length);
		}
		return signature;
	}

	protected boolean engineVerify(byte[] sigBytes) 
			throws SignatureException 
	{
		if(VERIFY_MODE)
		{
			BigInteger r;
			BigInteger s;
			// Split sigBytes into r and s!
			// r seems to consistently be 128 or 129 bytes long
			// s seems to be consistently 128 or 129 bytes long
			if (sigBytes.length == 256)
			{
				r = new BigInteger(Arrays.copyOfRange(sigBytes, 0, 128));
				s = new BigInteger(Arrays.copyOfRange(sigBytes, 128, sigBytes.length));				
			}
			else
			{
				r = new BigInteger(Arrays.copyOfRange(sigBytes, 0, 129));
				s = new BigInteger(Arrays.copyOfRange(sigBytes, 129, sigBytes.length));
			}
			
			// arg1 = message, arg2 & arg3 = signed hash
			return verify(new BigInteger(encoded_hash), new ElGamal_Ciphertext(r, s), pk);			
		}
		else
		{
			throw new SignatureException("Didn't Initialize Engine Verify Mode!");
		}
	}

	protected void engineSetParameter(String param, Object value) 
			throws InvalidParameterException 
	{

	}

	protected Object engineGetParameter(String param) 
			throws InvalidParameterException
	{
		return null;
	}
	
	public ElGamal_Ciphertext sign(BigInteger M)
	{
		BigInteger p1 = sk.p.subtract(BigInteger.ONE);
		BigInteger K = null;
		while(true)
		{
			// Pick [0, p - 2]
			K = NTL.RandomBnd(p1);
			// Need K [2, P - 2]
			if(K.equals(BigInteger.ONE) || K.equals(BigInteger.ZERO))
			{
				continue;
			}
			if(K.gcd(p1).equals(BigInteger.ONE))
			{
				break;
			}
		}
		BigInteger r = this.sk.g.modPow(K, sk.p);
	    BigInteger s = M.subtract(sk.x.multiply(r)).multiply(K.modInverse(p1)).mod(p1);
	    return new ElGamal_Ciphertext(r, s);
	}
	
	// https://en.wikipedia.org/wiki/ElGamal_signature_scheme
    public boolean verify(BigInteger M, ElGamal_Ciphertext sig, ElGamalPublicKey pk)
    {
    	BigInteger r = sig.getA();
    	BigInteger s = sig.getB();
    	BigInteger check = null;
    	
        if (r.compareTo(BigInteger.ZERO) <= 0 || r.compareTo(pk.p.subtract(BigInteger.ONE)) == 1)
        {
        	throw new IllegalArgumentException("Invalid r!");
        }
        if (s.compareTo(BigInteger.ZERO) <= 0 || s.compareTo(pk.p.subtract(TWO)) == 1)
        {
        	throw new IllegalArgumentException("Invalid s!");
        }
        // h = y = g^x
        check = pk.h.modPow(r, pk.p);
        check = check.multiply(r.modPow(s, pk.p)).mod(pk.p);
        if (check.compareTo(pk.g.modPow(M, pk.p)) == 0)
        {
        	return true;
        }
        return false;
    }
    
	// PUBLIC FACING FUNCTIONS
	public void initSign(ElGamalPrivateKey sk) throws InvalidKeyException
	{
		engineInitSign(sk);
	}
	
	public void initVerify(ElGamalPublicKey pk) throws InvalidKeyException
	{
		engineInitVerify(pk);
	}
	
	public void update(byte [] b) throws SignatureException
	{
		engineUpdate(b, 0, b.length);
	}
	
	public byte [] sign() throws SignatureException
	{
		return engineSign();
	}
	
	public boolean verify(byte [] signature) throws SignatureException
	{
		return engineVerify(signature);
	}
}
