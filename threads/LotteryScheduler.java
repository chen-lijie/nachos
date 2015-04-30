package nachos.threads;

import nachos.machine.*;

import java.util.NavigableSet;
import java.util.Random;
import java.util.TreeSet;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
	public LotteryScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer priority from
	 *            waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 1;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = Integer.MAX_VALUE;

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread
	 *            the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class LotteryQueue extends ThreadQueue implements
			Comparable<LotteryQueue> {
		LotteryQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
			// I did't implement the case for transferPriority is false since it
			// is not required.
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			int tmp = sumPriority;
			ThreadState state = getThreadState(thread);
			state.enqueueTime = enqueueId++;
			state.waitForAccess(this);
			waiters.add(state);
			tmp += state.effectivePriority;
			update(tmp);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			ThreadState state = getThreadState(thread);
			resourceHolder = state;
			state.acquire(this);
		}

		private ThreadState pickNextThread() {
			int rnd = rand.nextInt(sumPriority);
			for (ThreadState state : waiters) {
				rnd -= state.effectivePriority;
				if (rnd < 0)
					return state;
			}
			Lib.assertNotReached();
			return null;
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			if (resourceHolder != null) {
				resourceHolder.removeResource(this);
				resourceHolder = null;
			}
			if (waiters.isEmpty())
				return null;
			ThreadState state = pickNextThread();
			waiters.remove(state);
			Lib.assertTrue(state.waitingResource == this);
			KThread thread = state.thread;
			int tmp = sumPriority - state.effectivePriority;
			update(tmp);
			resourceHolder = state;
			state.waitingResource = null;
			state.addResource(this);
			return thread;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		private void update(int tmp) {
			if (tmp != sumPriority) {
				if (resourceHolder != null)
					resourceHolder.updateResource(this, tmp);
				else
					sumPriority = tmp;
			}
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		private int sumPriority;
		private ThreadState resourceHolder;
		private int enqueueId;
		private NavigableSet<ThreadState> waiters = new TreeSet<ThreadState>();
		private int id = currentPriorityQueueId++;

		protected void setSumPriority(int sumPriority) {
			this.sumPriority = sumPriority;
		}

		public int getSumPriority() {
			return sumPriority;
		}

		public void updateWaiter(ThreadState threadState, int effectivePriority) {
			int tmp = sumPriority - threadState.effectivePriority;
			waiters.remove(threadState);
			threadState.setEffectivePriority(effectivePriority);
			waiters.add(threadState);
			tmp += effectivePriority;
			update(tmp);
		}

		@Override
		public int compareTo(LotteryQueue o) {
			// TODO Auto-generated method stub
			int cmp = Integer.compare(sumPriority, o.sumPriority);
			if (cmp != 0)
				return -cmp;
			return Integer.compare(id, o.id);
		}
	}

	protected static int currentPriorityQueueId = 0;

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 *
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState implements Comparable<ThreadState> {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param thread
		 *            the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;

			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			// implement me
			return effectivePriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param priority
		 *            the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			int tmp = effectivePriority - this.priority;
			this.priority = priority;
			tmp += this.priority;
			update(tmp);
		}

		protected void updateResource(LotteryQueue resource, int sumPriority) {
			int tmp = effectivePriority - resource.sumPriority;
			resources.remove(resource);
			resource.setSumPriority(sumPriority);
			resources.add(resource);
			tmp += resource.sumPriority;
			update(tmp);
		}

		protected void addResource(LotteryQueue resource) {
			resources.add(resource);
			int tmp = effectivePriority + resource.sumPriority;
			update(tmp);
		}

		protected void removeResource(LotteryQueue resource) {
			resources.remove(resource);
			int tmp = effectivePriority - resource.sumPriority;
			update(tmp);
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 *
		 * @param waitQueue
		 *            the queue that the associated thread is now waiting on.
		 *
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(LotteryQueue waitQueue) {
			// implement me
			waitingResource = waitQueue;
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(LotteryQueue waitQueue) {
			// implement me
			addResource(waitQueue);
		}

		@Override
		public int compareTo(ThreadState o) {
			// TODO Auto-generated method stub
			int cmp = Integer.compare(effectivePriority, o.effectivePriority);
			if (cmp != 0)
				return -cmp;// reversing it so that bigger one comes first
			return Integer.compare(enqueueTime, o.enqueueTime);
		}

		private void update(int tmp) {
			if (tmp != effectivePriority) {
				if (waitingResource != null)
					waitingResource.updateWaiter(this, tmp);
				else
					effectivePriority = tmp;
			}
		}

		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		protected int enqueueTime;
		protected int effectivePriority = 0;// equal to priority if
											// transferPriority

		protected void setEffectivePriority(int effectivePriority) {
			this.effectivePriority = effectivePriority;
		}

		// is false
		protected LotteryQueue waitingResource;
		protected NavigableSet<LotteryQueue> resources = new TreeSet<LotteryQueue>();

	}

	private Random rand = new Random(199581);

	public static void selfTest1() {
		final Lock mutex = new Lock();
		Random rnd = new Random();

		boolean intStatus = Machine.interrupt().disable();
		for (int i = 0; i < 7; i++) {
			final int priority = rnd.nextInt(7) + 1;
			Runnable r = new Runnable() {
				@Override
				public void run() {
					// TODO Auto-generated method stub
					mutex.acquire();
					Lib.debug('m', "Priority: " + priority);
					mutex.release();
				}
			};
			KThread t = new KThread(r);
			ThreadedKernel.scheduler.setPriority(t, priority);
			t.setName("Thread " + i);
			t.fork();
		}
		Machine.interrupt().restore(intStatus);

		ThreadedKernel.alarm.waitUntil(10000);
	}

	public static void selfTest2() {
		final Lock mutex = new Lock();

		Runnable r = new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				mutex.acquire();
			}
		};
		ThreadedKernel.alarm.waitUntil(10000);

		boolean intStatus = Machine.interrupt().disable();
		for (int i = 0; i < 7; i++) {
			final int id = i;
			r = new Runnable() {
				@Override
				public void run() {
					// TODO Auto-generated method stub
					mutex.acquire();
					Lib.debug('m', "Priority: " + id);
					mutex.release();
				}
			};
			KThread t = new KThread(r);
			ThreadedKernel.scheduler.setPriority(t, i + 1);
			t.setName("Thread " + i);
			t.fork();
		}
		Machine.interrupt().restore(intStatus);

		ThreadedKernel.alarm.waitUntil(10000);
	}

	// explicit test of priority inversion
	public static void selfTest3() {
		final Lock mutex = new Lock();
		boolean intStatus = Machine.interrupt().disable();
		KThread t = new KThread(new Runnable() {
			@Override
			public void run() {
				mutex.acquire();
				int s = 0;
				for (int i = 0; i < 10; i++) {
					ThreadedKernel.alarm.waitUntil(1000);
					Lib.debug('m', "Low is happy " + i);
				}
				mutex.release();
			}
		});
		ThreadedKernel.scheduler.setPriority(t, 1);
		KThread t2 = new KThread(new Runnable() {
			@Override
			public void run() {
				ThreadedKernel.alarm.waitUntil(5000);
				mutex.acquire();
				int s = 0;
				for (int i = 0; i < 10; i++) {
					ThreadedKernel.alarm.waitUntil(1000);
					Lib.debug('m', "High is happy " + i);
				}
				mutex.release();
			}
		});
		ThreadedKernel.scheduler.setPriority(t2, 2);
		KThread t3 = new KThread(new Runnable() {
			@Override
			public void run() {
				int s = 0;
				for (int i = 0; i < 10; i++) {
					ThreadedKernel.alarm.waitUntil(1000);
					Lib.debug('m', "Middle is always happy " + i);
				}
			}
		});
		ThreadedKernel.scheduler.setPriority(t3, 1);
		t.fork();
		t2.fork();
		t3.fork();
		Machine.interrupt().restore(intStatus);
		ThreadedKernel.alarm.waitUntil(1000000);
	}
}
