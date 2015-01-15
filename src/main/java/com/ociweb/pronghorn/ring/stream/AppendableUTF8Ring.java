package com.ociweb.pronghorn.ring.stream;

import static com.ociweb.pronghorn.ring.RingBuffer.spinBlockOnTail;
import static com.ociweb.pronghorn.ring.RingBuffer.tailPosition;

import java.io.IOException;

import com.ociweb.pronghorn.ring.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.ring.RingBuffer;
import com.ociweb.pronghorn.ring.RingWriter;

public class AppendableUTF8Ring implements Appendable {

	private final RingBuffer ringBuffer;
	private final char[] temp = new char[1];
	private long outputTarget;
	private long tailPosCache;
	
	private int countDownInit = 0;
	private int countDown;
	
	public AppendableUTF8Ring(RingBuffer ringBuffer) {
		this.ringBuffer = ringBuffer;
		if (RingBuffer.from(ringBuffer) != FieldReferenceOffsetManager.RAW_BYTES) {
			throw new UnsupportedOperationException("This class can only be used with the very simple RAW_BYTES catalog of messages.");
		}
		int messagesPerRing = (1<<(ringBuffer.pBits-1));
		outputTarget = 2-messagesPerRing;//this value is negative		
		tailPosCache = tailPosition(ringBuffer);
		
		countDownInit = messagesPerRing>>2;
		countDown = countDownInit;
		
	}
	
	@Override
	public Appendable append(CharSequence csq) throws IOException {
		tailPosCache = spinBlockOnTail(tailPosCache, outputTarget, ringBuffer);
        outputTarget+=2;
		RingWriter.writeUTF8(ringBuffer, csq);
		
		if ((--countDown)<=0) {
			RingBuffer.publishWrites(ringBuffer);
			countDown = countDownInit;
		}
		return this;
	}

	@Override
	public Appendable append(CharSequence csq, int start, int end)
			throws IOException {
		tailPosCache = spinBlockOnTail(tailPosCache, outputTarget, ringBuffer);
        outputTarget+=2;
		RingWriter.writeUTF8(ringBuffer, csq, start, end-start);
		
		if ((--countDown)<=0) {
			RingBuffer.publishWrites(ringBuffer);
			countDown = countDownInit;
		}
		return this;
	}

	@Override
	public Appendable append(char c) throws IOException {
		tailPosCache = spinBlockOnTail(tailPosCache, outputTarget, ringBuffer);
        outputTarget+=2;
		temp[0]=c; //TODO: C, This should be optimized however callers should prefer to use the other two methods.
		RingWriter.writeUTF8(ringBuffer, temp);
		
		if ((--countDown)<=0) {
			RingBuffer.publishWrites(ringBuffer);
			countDown = countDownInit;
		}
		return this;
	}
	
	public void flush() {
		tailPosCache = spinBlockOnTail(tailPosCache, outputTarget, ringBuffer);
        outputTarget+=2;
		RingStreams.writeEOF(ringBuffer);
	}

}