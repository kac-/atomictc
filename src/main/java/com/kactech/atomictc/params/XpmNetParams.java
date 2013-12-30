package com.kactech.atomictc.params;

import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Utils;

public class XpmNetParams extends NetworkParameters {
	public XpmNetParams() {

		super();
		interval = INTERVAL;
		targetTimespan = TARGET_TIMESPAN;
		proofOfWorkLimit = Utils.decodeCompactBits(0x1d00ffffL);
		acceptableAddressCodes = new int[] { 23 };
		dumpedPrivateKeyHeader = 23 + 128;
		addressHeader = 23;
		port = 9911;
		packetMagic = 0xe4e7e5e7L;
		//genesisBlock.setDifficultyTarget(Prime.targetFromInt(6));
		genesisBlock.setTime(1373064429L);
		genesisBlock.setNonce(383);
		//genesisBlock.setPrimeChainMultiplier(BigInteger.valueOf(532541).multiply(BigInteger.valueOf(2l * 3 * 5 * 7 * 11 * 13 * 17 * 19 * 23)));
		//genesisBlock.setHeaderSize(87);
		if (false) {
			byte[] ser = genesisBlock.bitcoinSerialize();
			System.out.println(Utils.bytesToHexString(ser));
		}
		id = ID_MAINNET;
		subsidyDecreaseBlockCount = 210000;
		spendableCoinbaseDepth = 100;
		String genesisHash = genesisBlock.getHashAsString();
		dnsSeeds = new String[] { "primecoin.net", "tnseed.ppcoin.net" };
	}

	private static XpmNetParams instance;

	public static synchronized XpmNetParams get() {
		if (instance == null) {
			instance = new XpmNetParams();
		}
		return instance;
	}
}
