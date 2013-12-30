package com.kactech.atomictc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.spongycastle.util.encoders.Hex;

import com.google.bitcoin.core.Utils;

public class MapGsonStateStore implements TradeStateStore {
	Map<String, String> m = new HashMap<String, String>();

	@Override
	public TradeState getTradeState(byte[] id) {
		String s = m.get(Utils.bytesToHexString(id));
		return s == null ? null : Common.gson.fromJson(s, TradeState.class);
	}

	@Override
	public void saveTradeState(TradeState ts) {
		m.put(ts.id.s, Common.gson.toJson(ts));
	}

	@Override
	public Collection<byte[]> getTradeStates() {
		Collection<byte[]> l = new ArrayList<byte[]>();
		for (String s : m.keySet())
			l.add(Hex.decode(s));
		return l;
	}

	@Override
	public void removeTradeState(byte[] id) {
		m.remove(Utils.bytesToHexString(id));
	}
}