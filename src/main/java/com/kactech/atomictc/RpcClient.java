package com.kactech.atomictc;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.googlecode.jsonrpc4j.Base64;
import com.googlecode.jsonrpc4j.JsonRpcHttpClient;

public class RpcClient {
	JsonRpcHttpClient client;
	NetworkDetails network;

	public RpcClient() {
		// TODO Auto-generated constructor stub
	}

	public RpcClient(NetworkDetails network) throws Exception {
		super();
		this.network = network;
		initRpc();
	}

	void initRpc() throws Exception {
		File cfgFile = new File(System.getProperty("user.home"), "." + network.clientFolder);
		cfgFile = new File(cfgFile, network.clientConfig);
		Properties cfg = new Properties();
		cfg.load(new FileReader(cfgFile));

		URL url = new URL("http://localhost:" + network.rpcPort + "/");
		client = new JsonRpcHttpClient(url);
		Map<String, String> headers = new HashMap<String, String>(client.getHeaders());
		headers.put("Authorization",
				"Basic " + Base64.encodeBytes((cfg.getProperty("rpcuser") + ':' + cfg.getProperty("rpcpassword"))
						.getBytes("UTF-8")));
		client.setHeaders(headers);
	}

	Rpc.Unspent[] listUnspent(String... addrs) {
		return listUnspent(0, 9999999, addrs);
	}

	Rpc.Unspent[] listUnspent(int minconf, int maxconf, String... addrs) {
		try {
			return client.invoke("listunspent", new Object[] { minconf, maxconf, addrs },
					Rpc.Unspent[].class);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	String dumpPrivKey(String addr) {
		try {
			return client.invoke("dumpprivkey", new Object[] { addr },
					String.class);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
}