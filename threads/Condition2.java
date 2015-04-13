package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 *
	 * @param conditionLock
	 *            the lock associated with this condition variable. The current
	 *            thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 *            <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		waiterQueue.waitForAccess(KThread.currentThread());
		conditionLock.release();
		KThread.sleep();
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		KThread thread = waiterQueue.nextThread();
		if (thread != null) {
			thread.ready();
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		for (;;) {
			KThread thread = waiterQueue.nextThread();
			if (thread != null) {
				thread.ready();
			} else {
				break;
			}
		}

		Machine.interrupt().restore(intStatus);
	}
	
	//test by writing a consumer-producer problem
	static class VolatileInt{
		public volatile int value=0;
	};
	public static void selfTest(){
		final Lock lock=new Lock();
		final Condition2 condition=new Condition2(lock);
		final VolatileInt good=new VolatileInt();
		KThread t1=new KThread(new Runnable(){
			@Override
			public void run(){
				for (int i=0;i<3;i++){
					lock.acquire();
					Lib.debug('m',"one produced");
					good.value=good.value+1;
					condition.wake();
					lock.release();
					ThreadedKernel.alarm.waitUntil(1000);
				}
			}
		});
		KThread t2=new KThread(new Runnable(){
			@Override
			public void run(){
				ThreadedKernel.alarm.waitUntil(10000);
				lock.acquire();
				Lib.debug('m',"three produced");
				good.value=good.value+3;
				condition.wakeAll();
				lock.release();
			}
		});
		Runnable r_consume=new Runnable(){
			@Override
			public void run(){
				for (int i=0;i<2;i++){
					lock.acquire();
					while (good.value==0){
						condition.sleep();
					}
					good.value=good.value-1;
					Lib.debug('m',"one consumed");
					lock.release();
				}
			}
		};
		KThread t3=new KThread(r_consume);
		KThread t4=new KThread(r_consume);
		KThread t5=new KThread(r_consume);
		t1.fork();
		t2.fork();
		t3.fork();
		t4.fork();
		t5.fork();
		t1.join();
		t2.join();
		t3.join();
		t4.join();
		t5.join();
	}

	private Lock conditionLock;

	private ThreadQueue waiterQueue = ThreadedKernel.scheduler
			.newThreadQueue(true);
}
