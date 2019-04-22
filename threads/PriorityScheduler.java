package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
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
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
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
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

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
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	    if (transferPriority)
	    	holding = getThreadState(thread);
	}

	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me
	    ThreadState t = pickNextThread();
	    if (t != null) {
	    	waitingQueue.remove(t);
	    	return t.thread;
	    }
	    else
	    	return null;
	}

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
	    // implement me
		ThreadState first = waitingQueue.peek();
		if (holding != null) {
			holding.holdSource.remove(this);
			holding.getEffectivePriority();
			holding = first;
		}
		if (first != null)
			first.waiting = null;
		return first;
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me (if you want)
	}
	
	public void add(ThreadState thread) {
		waitingQueue.add(thread);
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
	private java.util.PriorityQueue<ThreadState> waitingQueue = new java.util.PriorityQueue<ThreadState>();
	private ThreadState holding = null;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState implements Comparable<ThreadState> {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    this.startWaitingTime = 0;
	    setPriority(priorityDefault);
	    getEffectivePriority();
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
	    // implement me
	    int t = priority;
	    if (!holdSource.isEmpty()) {    	
	    	Iterator<PriorityQueue> iter = holdSource.iterator();
	    	while (iter.hasNext()) {
	    		PriorityQueue source = (PriorityQueue) iter.next();
	    		int max = 0;
	    		if (!source.waitingQueue.isEmpty())
	    			max = source.waitingQueue.peek().effectivePriority;

	    		if (max > t)
	    			t = max;
	    	}
	    }
	    
	    if (waiting != null && t != effectivePriority) {
	    	((PriorityQueue) waiting).waitingQueue.remove(this);
	    	this.effectivePriority = t;
	    	((PriorityQueue) waiting).waitingQueue.add(this);
	    }
	    
	    
        if (holding != null)
	    	holding.getEffectivePriority();
        effectivePriority = t;
        
	    return effectivePriority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    this.priority = priority;
	    getEffectivePriority();
	    // implement me
	}
	
	public void setTime(long time) {
		this.startWaitingTime = time;
	}
	

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
	    // implement me
		Lib.assertTrue(Machine.interrupt().disabled());
		long now = Machine.timer().getTime();
		this.setTime(now);
		this.waiting = waitQueue;
		holding = waitQueue.holding;
		waitQueue.add(this);
		getEffectivePriority();
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    // implement me
		Lib.assertTrue(Machine.interrupt().disabled());
		if (waitQueue.transferPriority)
			holdSource.add(waitQueue);
	}	
	
	@Override
	public int compareTo(ThreadState another) {
		if(this.effectivePriority - another.effectivePriority != 0)
			return  another.effectivePriority - this.effectivePriority;
		else 
			return (int)(this.startWaitingTime - another.startWaitingTime);
	}

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	protected long startWaitingTime;
	/** The priority of the associated thread. */
	protected int priority;
	protected int effectivePriority;
	protected ThreadQueue waiting = null;
	protected ThreadState holding = null;
	protected LinkedList<PriorityQueue> holdSource = new LinkedList<PriorityQueue>();
	
    }
    
    //selftest
    public static void selfTest() {
		ThreadQueue tq1 = ThreadedKernel.scheduler.newThreadQueue(true), tq2 = ThreadedKernel.scheduler.newThreadQueue(true), tq3 = ThreadedKernel.scheduler.newThreadQueue(true);
		KThread kt_1 = new KThread(), kt_2 = new KThread(), kt_3 = new KThread(), kt_4 = new KThread();
		
		boolean status = Machine.interrupt().disable();
		
        tq1.acquire(kt_2);
		tq2.acquire(kt_3);
		tq3.acquire(kt_4);
		
		tq1.waitForAccess(kt_1);
		tq2.waitForAccess(kt_2);
		tq3.waitForAccess(kt_3);
			
		ThreadedKernel.scheduler.setPriority(kt_1, 6);
		
		Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(kt_4)==6);
				
		KThread kt_5 = new KThread();
		kt_1.setName("1");
		kt_2.setName("2");
		kt_3.setName("3");
		kt_4.setName("4");
		kt_5.setName("5");

		System.out.println();
		ThreadedKernel.scheduler.setPriority(kt_5, 7);
		System.out.println();
		tq1.waitForAccess(kt_5);
		
		Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(kt_4)==7);
		
		tq1.nextThread();
		
		Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(kt_4)==1);
		
		Machine.interrupt().restore(status);
	}
}
