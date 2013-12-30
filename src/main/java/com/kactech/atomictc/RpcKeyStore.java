package com.kactech.atomictc;

import java.util.HashMap;
import java.util.Map;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.DumpedPrivateKey;
import com.google.bitcoin.core.ECKey;

public class RpcKeyStore implements KeyStore {
	RpcClient client;

	public RpcKeyStore(RpcClient client) {
		super();
		this.client = client;
	}

	@Override
	public ECKey[] getKeys(byte[]... pubKeyHashes) {
		ECKey[] keys = new ECKey[pubKeyHashes.length];
		Map<String, ECKey> cache = new HashMap<String, ECKey>();
		for (int i = 0; i < pubKeyHashes.length; i++) {
			//for (byte[] pub : pubKeyHashes) {
			String addr = new Address(client.network.params, pubKeyHashes[i]).toString();
			//System.out.println("priv key for "+addr);
			keys[i] = cache.get(addr);
			if (keys[i] == null) {
				try {
					DumpedPrivateKey dk = new DumpedPrivateKey(client.network.params, client.dumpPrivKey(addr));

					//System.out.println("dump " + dk.getKey().toAddress(client.network.params));
					//System.out.println("Dump "
					//		+ new ECKey(new BigInteger(1, dk.getKey().getPrivKeyBytes()), null, true).toAddress(client.network.params));
					cache.put(addr, keys[i] =
							dk.getKey()
							//.getPrivKeyBytes()
							);
				} catch (AddressFormatException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return keys;
	}

}