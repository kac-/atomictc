package com.kactech.atomictc;

import java.util.Collection;

public interface TradeStateStore {
	public TradeState getTradeState(byte[] id);

	public void saveTradeState(TradeState ts);

	public Collection<byte[]> getTradeStates();

	public void removeTradeState(byte[] id);
}