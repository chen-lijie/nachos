package nachos.threads;

import nachos.machine.*;

import java.util.NavigableSet;
import java.util.Random;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
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
		return new PriorityQueue(transferPriority);
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
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread
	 *            the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	private ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue implements
			Comparable<PriorityQueue> {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
			// I did't implement the case for transferPriority is false since it
			// is not required.
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			ThreadState state = getThreadState(thread);
			state.enqueueTime = enqueueId++;
			state.waitForAccess(this);
			waiters.add(state);
			update();
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			ThreadState state = getThreadState(thread);
			resourceHolder = state;
			state.acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			if (resourceHolder != null) {
				resourceHolder.removeResource(this);
				resourceHolder = null;
			}
			if (waiters.isEmpty())
				return null;
			ThreadState state = waiters.pollFirst();
			Lib.assertTrue(state.waitingResource == this);
			KThread thread = state.thread;
			update();
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
		protected ThreadState pickNextThread() {
			// implement me
			if (waiters.isEmpty())
				return null;
			return waiters.first();
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		private void update() {
			// System.out.println("updating");
			int tmp = priorityMinimum;
			if (!waiters.isEmpty()) {
				tmp = waiters.first().effectivePriority;
			}
			if (tmp != maxPriority) {
				if (resourceHolder != null)
					resourceHolder.updateResource(this, tmp);
				else
					maxPriority = tmp;
			}
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		private int maxPriority;
		private ThreadState resourceHolder;
		private int enqueueId;
		private NavigableSet<ThreadState> waiters = new TreeSet<ThreadState>();
		private int id = currentPriorityQueueId++;

		protected void setMaxPriority(int maxPriority) {
			this.maxPriority = maxPriority;
		}

		public int getMaxPriority() {
			return maxPriority;
		}

		public void updateWaiter(ThreadState threadState, int tmp) {
			waiters.remove(threadState);
			threadState.setEffectivePriority(tmp);
			waiters.add(threadState);
			update();
		}

		@Override
		public int compareTo(PriorityQueue o) {
			// TODO Auto-generated method stub
			int cmp = Integer.compare(maxPriority, o.maxPriority);
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
	private class ThreadState implements Comparable<ThreadState> {
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

			this.priority = priority;
			update();
		}

		protected void updateResource(PriorityQueue resource, int maxPriority) {
			resources.remove(resource);
			resource.setMaxPriority(maxPriority);
			resources.add(resource);
			update();
		}

		protected void addResource(PriorityQueue resource) {
			resources.add(resource);
			update();
		}

		protected void removeResource(PriorityQueue resource) {
			resources.remove(resource);
			update();
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
		public void waitForAccess(PriorityQueue waitQueue) {
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
		public void acquire(PriorityQueue waitQueue) {
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

		private void update() {
			int tmp = priority;
			if (!resources.isEmpty()) {
				tmp = Math.max(tmp, resources.first().getMaxPriority());
			}

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
		protected int effectivePriority = -1;// equal to priority if
												// transferPriority

		protected void setEffectivePriority(int effectivePriority) {
			this.effectivePriority = effectivePriority;
		}

		// is false
		protected PriorityQueue waitingResource;
		protected NavigableSet<PriorityQueue> resources = new TreeSet<PriorityQueue>();

	}

	public static void selfTest1() {
		final Lock mutex = new Lock();
		Random rnd = new Random();

		boolean intStatus = Machine.interrupt().disable();
		for (int i = 0; i < 7; i++) {
			final int priority = rnd.nextInt(7);
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
			ThreadedKernel.scheduler.setPriority(t, i);
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
		ThreadedKernel.scheduler.setPriority(t, 0);
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
