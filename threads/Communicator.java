package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		mutex = new Lock();
		waitS = new Condition(mutex);
		waitL = new Condition(mutex);
		waitToTake = new Condition(mutex);
		waitTaken = new Condition(mutex);
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 *
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 *
	 * @param word
	 *            the integer to transfer.
	 */
	public void speak(int word) {
		mutex.acquire();
		while (!(AS == 0 && AL > 0)) {
			WS++;
			waitS.sleep();
			WS--;
		}
		AS++;
		temp = word;
		waitToTake.wake();
		waitTaken.sleep();
		AS--;
		if (WS > 0)
			waitS.wake();
		mutex.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return the integer transferred.
	 */
	public int listen() {
		mutex.acquire();
		while (!(AL == 0)) {
			WL++;
			waitL.sleep();
			WL--;
		}
		AL++;
		if (WS > 0)
			waitS.wake();
		waitToTake.sleep();
		int ret = temp;// take temp
		waitTaken.wake();
		AL--;
		if (WL > 0)
			waitL.wake();
		mutex.release();
		return ret;
	}
	
	public static void selfTest(){
		final Communicator com=new Communicator();
		final int[] role=new int[]{0,1,1,0,0,0,0,1,1,1};
		for (int i=0;i<10;i++){
			final int ii=i;
			KThread t=new KThread(new Runnable(){
				@Override
				public void run(){
					if (role[ii]==0){
						Lib.debug('m',"speaker start "+ii);
						com.speak(ii);
						Lib.debug('m',"end "+ii);
					}else{
						Lib.debug('m',"listener start "+ii);
						int a=com.listen();
						Lib.debug('m',"end "+ii+" get "+a);
					}
				}
			});
			t.fork();
		}
		ThreadedKernel.alarm.waitUntil(100000);
	}

	private Condition waitS, waitL, waitToTake, waitTaken;
	private Lock mutex;
	private int AS = 0, AL = 0, WS = 0, WL = 0;
	private int temp;
}
