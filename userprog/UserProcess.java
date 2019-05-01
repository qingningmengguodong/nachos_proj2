package nachos.userprog;

import nachos.machine.*;

import nachos.threads.*;
import nachos.userprog.*;



import java.io.EOFException;
import java.util.Map;
import java.util.HashMap;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
	pageTable = new TranslationEntry[numPhysPages];
	processID = UserKernel.nextProcessID++;
	UserKernel.activeProcessCount++;
	//for (int i=0; i<numPhysPages; i++)
	//    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
	for (int i=2; i<16; i++) {
		FileDescriptorUsed[i] = false;
		FilePosition[i] = 0;
	}
	FileDescriptorUsed[0] = FileDescriptorUsed[1] = true;
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int VPN = Processor.pageFromAddress(vaddr);
	int OFFSET = Processor.offsetFromAddress(vaddr);
	int PPN = pageTable[VPN].ppn;
	int paddr = Processor.makeAddress(PPN, OFFSET);
	int amount = Math.min(length, memory.length-paddr);
	System.arraycopy(memory, paddr, data, offset, amount);

	return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int VPN = Processor.pageFromAddress(vaddr);
	int OFFSET = Processor.offsetFromAddress(vaddr);
	int PPN = pageTable[VPN].ppn;
	int paddr = Processor.makeAddress(PPN, OFFSET);
	int amount = Math.min(length, memory.length-paddr);
	System.arraycopy(data, offset, memory, paddr, amount);

	return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }
    
    private int getFirstFreePage() {
    	for (int i = 0; i < numPhysPages; i++)
    		if (!UserKernel.PhysPageUsed[i]) {
    			UserKernel.PhysPageUsed[i] = true;
    			return i;
    		}
    	return -1;
    }
    
    private void releasePage(int i) {
    	UserKernel.PhysPageUsed[i] = false;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");
		      
	    for (int i=0; i<section.getLength(); i++) {
		int vpn = section.getFirstVPN()+i;
		int ppn = getFirstFreePage();
		pageTable[vpn] = new TranslationEntry(vpn, ppn, true,false,false,false);

		// for now, just assume virtual addresses=physical addresses
		section.loadPage(i, ppn);
	    }
	}
	
	//load stack and the last page containing arguments
	for (int i=0; i<stackPages+1; i++) {
		int vpn = numPages-1-stackPages+i;
		int ppn = getFirstFreePage();
		pageTable[vpn] = new TranslationEntry(vpn, ppn, true,false,false,false);
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    	for (int i=0; i<numPages; i++) {
			int ppn = pageTable[i].ppn;
			releasePage(ppn);
		}
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {
    // if the process is not the root process, return immediately
    if (processID != 1)
    	return 0;
    
	Machine.halt();	
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }
    
    /**
     * Handle the create() system call.
     */
    private int handleCreateOrOpen(String FileName, boolean create) {    	
    OpenFile f = ThreadedKernel.fileSystem.open(FileName, create);
    if (f == null) {
    	System.out.println("file does not exist");
    	return -1;
    }
    
    int fileDescriptor = -1;
    for (int i = 2; i < 16; i++) 
    	if (!FileDescriptorUsed[i]) {
    		fileDescriptor = i;
    		FileDescriptorUsed[i] = true;
    		break;
    	}
    if (fileDescriptor == -1)
    	return -1;
    
    FileTable.put(fileDescriptor, f);
    return fileDescriptor;
    }
    
    private int handleCreate(String FileName) {
    	return handleCreateOrOpen(FileName, true);
    }
    
    private int handleOpen(String FileName) {
    	return handleCreateOrOpen(FileName, false);
    }
    
    private int handleClose(int fileDescriptor) {
    	OpenFile f = (OpenFile)FileTable.get(fileDescriptor);
		f.close();
		FileDescriptorUsed[fileDescriptor] = false;
		FileTable.remove(fileDescriptor);
		return 0;
    }


    private static final int
        syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
    //System.out.println(syscall);
	switch (syscall) {
	case syscallHalt: 
	    return handleHalt();
	case syscallExit: {
		//Close open files
		for (int i=2; i<16; i++)
			if (FileDescriptorUsed[i] == true) 
				handleClose(i);
		
		//Free up memory
		unloadSections();
		
		UserKernel.activeProcessCount--;
		if (UserKernel.activeProcessCount>0)
			KThread.currentThread().finish();
		else
			Kernel.kernel.terminate();
		break;
	}
	case syscallExec: {
		String FileName = readVirtualMemoryString(a0, 256);
		String[] argv = new String[0];
		UserKernel.sonProcess[processID][UserKernel.nextProcessID] = true;
		
		UserProcess P = UserProcess.newUserProcess();		
		Lib.assertTrue(P.execute(FileName, new String[] { }));

		break;
	}
	case syscallJoin: {
		break;
	}
	case syscallCreate: {
		String FileName = readVirtualMemoryString(a0, 256);
		return handleCreate(FileName);
	}
	case syscallOpen: {
		String FileName = readVirtualMemoryString(a0, 256);
		return handleOpen(FileName);
	}
	case syscallRead: {
		OpenFile f = (OpenFile)FileTable.get(a0);
		byte[] buf = new byte[a2+1];
		int readLength = f.read(FilePosition[a0], buf, 0, a2);
		if (readLength != -1) {
			writeVirtualMemory(a1, buf);
			FilePosition[a0] += readLength;
 		}
		//String content = new String(buf);
		//System.out.println(content);
		//System.out.println(readLength);
		return readLength;
	}
	case syscallWrite: {
		OpenFile f = (OpenFile)FileTable.get(a0);
		byte[] buf = new byte[256];
		//String str = readVirtualMemoryString(a1, 256);
		//System.out.println(str);
		//buf = str.getBytes();
		readVirtualMemory(a1, buf);
		
		//String str = "I am so good!";
		//byte[] buf = str.getBytes();
		int writeLength = f.write(FilePosition[a0], buf, 0, a2);
		if (writeLength == -1 || writeLength < a2)
			return -1;
		FilePosition[a0] += writeLength;
		return writeLength;
	}
	case syscallClose: {
		return handleClose(a0);
	}
	case syscallUnlink: {
		String FileName = readVirtualMemoryString(a0, 256);
		if (!ThreadedKernel.fileSystem.remove(FileName))
			return -1;
		return 0;
	}

	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
    private HashMap FileTable = new HashMap(16);
	private boolean[] FileDescriptorUsed = new boolean[16];
	private int [] FilePosition = new int[16];
	private int numPhysPages = Machine.processor().getNumPhysPages();
	private int processID;
    
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
}
