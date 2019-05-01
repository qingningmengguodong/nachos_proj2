package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;

import java.util.Random;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

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
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends Scheduler{
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }
    
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	// implement me
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
    public static final int priorityMinimum = 1;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = Integer.MAX_VALUE;    

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
    protected class LotteryQueue extends ThreadQueue {
	LotteryQueue(boolean transferPriority) {
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
		int total_lottery = 0;
		Iterator iter = waitingQueue.iterator();
		while(iter.hasNext()) 
			total_lottery += ((ThreadState)iter.next()).effectivePriority;
		
		ThreadState chosen;
		Random ra = new Random();
		int flag = ra.nextInt(total_lottery);
		
		int tmp = 0;
		iter = waitingQueue.iterator();
		ThreadState last, now;
		last = (ThreadState)iter.next();
		if (last != null)
		    tmp += last.effectivePriority;
		while(iter.hasNext()) {
			if (flag < tmp)
				return last;
			now = (ThreadState)iter.next();
			tmp += now.effectivePriority;
			last = now;
		}
		
		if (holding != null) {
			holding.holdSource.remove(this);
			holding.getEffectivePriority();
			holding = last;
		}
		if (last != null)
			last.waiting = null;
		
		return last;
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
	    	Iterator<LotteryQueue> iter = holdSource.iterator();
	    	while (iter.hasNext()) {
	    		LotteryQueue source = (LotteryQueue) iter.next();
	    		int total_lottery = 0;
	    		Iterator it = source.waitingQueue.iterator();
	    		while (it.hasNext())
	    			total_lottery += ((ThreadState)it.next()).effectivePriority;
	    		t += total_lottery;
	    	}
	    }
	    
	    if (waiting != null && t != effectivePriority) {
	    	((LotteryQueue) waiting).waitingQueue.remove(this);
	    	this.effectivePriority = t;
	    	((LotteryQueue) waiting).waitingQueue.add(this);
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
	public void waitForAccess(LotteryQueue waitQueue) {
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
	public void acquire(LotteryQueue waitQueue) {
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
	protected LinkedList<LotteryQueue> holdSource = new LinkedList<LotteryQueue>();
    }
   
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
		
		Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(kt_4)==9);
				
		KThread kt_5 = new KThread();
		kt_1.setName("1");
		kt_2.setName("2");
		kt_3.setName("3");
		kt_4.setName("4");
		kt_5.setName("5");

		ThreadedKernel.scheduler.setPriority(kt_5, 7);
		tq1.waitForAccess(kt_5);
		
		Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(kt_4)==16);
		
		System.out.println(tq1.nextThread().getName());
		
		Machine.interrupt().restore(status);
	}
}
