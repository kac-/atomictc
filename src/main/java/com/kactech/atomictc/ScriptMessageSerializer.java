package com.kactech.atomictc;

import java.nio.charset.Charset;
import java.util.List;

import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.VarInt;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.bitcoin.script.ScriptChunk;
import com.google.bitcoin.script.ScriptOpCodes;

public class ScriptMessageSerializer implements MessageSerializer {

	@Override
	public TradeMessage deserialize(byte[] msg) {
		Script scr, in;
		try {
			scr = new Script(msg);
			in = new Script(scr.getChunks().get(0).data);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		List<ScriptChunk> chunks = in.getChunks();
		TradeMessage tm = null;
		int op = chunks.get(0).data[0];
		switch (op) {
		case ScriptOpCodes.OP_0:
			TradeMessage.Init init = new TradeMessage.Init();
			init.bailInHash = chunks.get(1).data;
			init.pubKey = chunks.get(2).data;
			init.hisPubKey = chunks.get(3).data;
			init.hx = chunks.get(4).data;
			init.send = new String(chunks.get(5).data, Common.ASCII);
			init.receive = new String(chunks.get(6).data, Common.ASCII);
			init.lockTime = new VarInt(chunks.get(7).data, 0).value;
			init.amount = new VarInt(chunks.get(8).data, 0).value;
			init.refundAmount = new VarInt(chunks.get(9).data, 0).value;
			tm = init;
			break;
		case ScriptOpCodes.OP_1:
			TradeMessage.Accept acc = new TradeMessage.Accept();
			acc.id = chunks.get(1).data;
			acc.sigHisRefund = chunks.get(2).data;
			acc.bailInHash = chunks.get(3).data;
			acc.lockTime = new VarInt(chunks.get(4).data, 0).value;
			acc.amount = new VarInt(chunks.get(5).data, 0).value;
			acc.refundAmount = new VarInt(chunks.get(6).data, 0).value;
			tm = acc;
			break;
		case ScriptOpCodes.OP_2:
			TradeMessage.FirstBail fbail = new TradeMessage.FirstBail();
			fbail.id = chunks.get(1).data;
			fbail.sigHisRefund = chunks.get(2).data;
			fbail.bailIn = chunks.get(3).data;
			tm = fbail;
			break;
		case ScriptOpCodes.OP_3:
			TradeMessage.Bail bail = new TradeMessage.Bail();
			bail.id = chunks.get(1).data;
			bail.bailIn = chunks.get(2).data;
			tm = bail;
			break;
		default:
			throw new RuntimeException("uknown messace type: " + op);
		}
		tm.bytes = scr.getChunks().get(0).data;
		tm.signature = scr.getChunks().get(1).data;
		return tm;

	}

	@Override
	public byte[] serialize(TradeMessage msg) {
		if (msg instanceof TradeMessage.Init) {
			TradeMessage.Init m = (TradeMessage.Init) msg;
			msg.bytes = new ScriptBuilder()
					.op(ScriptOpCodes.OP_0)
					.data(m.bailInHash)
					.data(m.pubKey)
					.data(m.hisPubKey)
					.data(m.hx)
					.data(m.send.getBytes(Common.ASCII))
					.data(m.receive.getBytes(Common.ASCII))
					.data(new VarInt(m.lockTime).encode())
					.data(new VarInt(m.amount).encode())
					.data(new VarInt(m.refundAmount).encode())
					.build().getProgram();
		} else if (msg instanceof TradeMessage.Accept) {
			TradeMessage.Accept m = (TradeMessage.Accept) msg;
			msg.bytes = new ScriptBuilder()
					.op(ScriptOpCodes.OP_1)
					.data(m.id)
					.data(m.sigHisRefund)
					.data(m.bailInHash)
					.data(new VarInt(m.lockTime).encode())
					.data(new VarInt(m.amount).encode())
					.data(new VarInt(m.refundAmount).encode())
					.build().getProgram();
		} else if (msg instanceof TradeMessage.FirstBail) {
			TradeMessage.FirstBail m = (TradeMessage.FirstBail) msg;
			msg.bytes = new ScriptBuilder().op(ScriptOpCodes.OP_2)
					.data(Utils.doubleDigest(m.id))
					.data(m.sigHisRefund)
					.data(m.bailIn)
					.build().getProgram();
		} else if (msg instanceof TradeMessage.Bail) {
			TradeMessage.Bail m = (TradeMessage.Bail) msg;
			msg.bytes = new ScriptBuilder().op(ScriptOpCodes.OP_3)
					.data(Utils.doubleDigest(m.id))
					.data(m.bailIn)
					.build().getProgram();
		} else
			throw new RuntimeException("unsupported message " + msg.getClass().getName());
		return msg.bytes;
	}

	public byte[] wrapWithSignature(TradeMessage msg) {
		return new ScriptBuilder().data(msg.bytes).data(msg.signature).build().getProgram();
	}

}