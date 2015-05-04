package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.text.AsyncBoxView.ChildLocator;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		boolean intStatus = Machine.interrupt().disable();
		// disable here to make sure processId is unique
		processId = processCounter++;
		fileList = new OpenFile[MAX_FILE_OPEN];
		fileList[0] = UserKernel.console.openForReading();
		fileList[1] = UserKernel.console.openForWriting();

		// comment them out
		// int numPhysPages = Machine.processor().getNumPhysPages();
		// pageTable = new TranslationEntry[numPhysPages];
		// for (int i = 0; i < numPhysPages; i++)
		// pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		//return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		return new UserProcess();
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		++activeProcess;
		// save the thread here
		thread = new UThread(this);
		thread.setName(name).fork();

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
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including
	 *            the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	// translate the virtual address to physical address, -1 if error occurs
	private int virtualToPhysical(int vaddr, TranslationEntry entry,
			boolean write) {
		if (!entry.valid || entry.readOnly && write)
			return -1;
		int offset = vaddr - entry.vpn * pageSize;
		return entry.ppn * pageSize + offset;
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param length
	 *            the number of bytes to transfer from virtual memory to the
	 *            array.
	 * @return the number of bytes successfully transferred.
	 */

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		if (vaddr < 0 || vaddr >= numPages * pageSize)
			return 0;

		if (vaddr + length > numPages * pageSize)
			length = numPages * pageSize - vaddr;

		// special check for length==0
		if (length == 0)
			return 0;

		byte[] memory = Machine.processor().getMemory();

		int startAddr = vaddr;
		int endAddr = vaddr + length - 1;
		int startPage = Machine.processor().pageFromAddress(startAddr);
		int endPage = Machine.processor().pageFromAddress(endAddr);

		int total = 0;

		for (int page = startPage; page <= endPage; ++page) {
			int start = Math.max(startAddr, page * pageSize);
			int end = Math.min(endAddr, (page + 1) * pageSize - 1);
			TranslationEntry entry = pageTable[page];

			int phy = virtualToPhysical(start, entry, false);
			if (phy < 0)
				break;
			System.arraycopy(memory, phy, data, offset + total, end - start + 1);

			total += end - start + 1;
			entry.used = true;
		}

		return total;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param length
	 *            the number of bytes to transfer from the array to virtual
	 *            memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		if (vaddr < 0 || vaddr >= numPages * pageSize)
			return 0;
		if (vaddr + length > numPages * pageSize)
			length = numPages * pageSize - vaddr;

		// special check for length==0
		if (length == 0)
			return 0;

		byte[] memory = Machine.processor().getMemory();

		int startAddr = vaddr;
		int endAddr = vaddr + length - 1;
		int startPage = Machine.processor().pageFromAddress(startAddr);
		int endPage = Machine.processor().pageFromAddress(endAddr);

		int total = 0;

		for (int page = startPage; page <= endPage; ++page) {
			int start = Math.max(startAddr, page * pageSize);
			int end = Math.min(endAddr, (page + 1) * pageSize - 1);
			TranslationEntry entry = pageTable[page];

			int phy = virtualToPhysical(start, entry, true);
			if (phy < 0)
				break;
			System.arraycopy(data, offset + total, memory, phy, end - start + 1);

			total += end - start + 1;
			entry.used = true;
			entry.dirty = true;
		}

		return total;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
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
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}
		
		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
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
		for (int i = 0; i < args.length; i++) {
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
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;
		if (!loadSections()){
			coff.close();
			return false;
		}
		coff.close();
		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 *
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		UserKernel.pageMutex.acquire();
		LinkedList<Integer> avaPages = UserKernel.avaPages;

		if (numPages > avaPages.size()) {
			UserKernel.pageMutex.release();
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// set pageTable
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			int ppn = avaPages.poll();
			pageTable[i] = new TranslationEntry(i, ppn, true, false, false,
					false);
		}
		UserKernel.pageMutex.release();

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				section.loadPage(i, pageTable[vpn].ppn);

				pageTable[vpn].readOnly = section.isReadOnly();
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.pageMutex.acquire();
		LinkedList<Integer> avaPages = UserKernel.avaPages;

		for (int i = 0; i < numPages; i++) {
			avaPages.add(pageTable[i].ppn);
		}

		UserKernel.pageMutex.release();

		// close all the file that it opened
		for (int i = 0; i < fileList.length; i++) {
			if (fileList[i] != null)
				handleClose(i);
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
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

		if (processId != ROOT_PROCESS)
			return -1;

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int handleCreate(int address) {
		if (address < 0)
			return -1;
		String file = readVirtualMemoryString(address, 256);
		if (file == null)
			return -1;
		int idx = -1;
		for (int i = 0; i < fileList.length; i++) {
			if (fileList[i] == null) {
				idx = i;
				break;
			}
		}
		if (idx == -1)
			return -1;
		if (!UserKernel.fileManager.create(file))
			return -1;
		OpenFile openFile = UserKernel.fileSystem.open(file, true);
		if (openFile == null)
			return -1;
		fileList[idx] = openFile;
		return idx;
	}

	private int handleOpen(int address) {
		if (address < 0)
			return -1;
		String file = readVirtualMemoryString(address, 256);
		if (file == null)
			return -1;
		int idx = -1;
		for (int i = 0; i < fileList.length; i++) {
			if (fileList[i] == null) {
				idx = i;
				break;
			}
		}
		
		if (idx == -1)
			return -1;
		
		OpenFile openFile = UserKernel.fileSystem.open(file, false);
		if (openFile == null)
			return -1;
		if (!UserKernel.fileManager.open(file))
			return -1;
		
		fileList[idx] = openFile;
		return idx;
	}

	private int handleClose(int idx) {
		if (idx < 0 || idx >= fileList.length)
			return -1;
		OpenFile openFile = fileList[idx];
		String name = openFile.getName();
		openFile.close();
		fileList[idx] = null;
		if (UserKernel.fileManager.close(name))
			return 0;
		else
			return -1;
	}

	private int handleRead(int idx, int address, int count) {
		if (idx < 0 || idx >= fileList.length || address < 0 || count < 0)
			return -1;
		OpenFile openFile = fileList[idx];
		if (openFile == null)
			return -1;
		int total = 0;
		while (count > 0) {
			int read = Math.min(count, MAX_BUFFER_SIZE);
			int got = openFile.read(BUFFER, 0, read);

			if (got < 0)
				return -1;

			// It is an error if some address are not valid.
			int wrote = writeVirtualMemory(address, BUFFER, 0, got);
			if (wrote < got)
				return -1;
			total += got;
			address += got;

			count -= got;
			if (got < read)
				break;
		}

		return total;
	}

	private int handleWrite(int idx, int address, int count) {
		if (idx < 0 || idx >= fileList.length || address < 0 || count < 0)
			return -1;
		OpenFile openFile = fileList[idx];
		if (openFile == null)
			return -1;
		int total = 0;
		while (count > 0) {
			int read = Math.min(count, MAX_BUFFER_SIZE);
			int got = readVirtualMemory(address, BUFFER, 0, read);

			// It is an error if we cannot read enough bytes from address
			if (got < read)
				return -1;
			int wrote = openFile.write(BUFFER, 0, read);
			if (wrote < 0){
				return -1;
			}
			total += got;
			address += got;

			count -= got;
			if (wrote < read)
				break;
		}

		return total;
	}

	private int handleUnlink(int address) {
		if (address < 0)
			return -1;
		String file = readVirtualMemoryString(address, 256);
		if (file == null)
			return -1;
		
		if (UserKernel.fileManager.unlink(file))
			return 0;
		return -1;
	}

	private int handleExit(int status) {
		Machine.interrupt().disable();
		// disable here ... to make life easier <_<
		unloadSections();

		for (UserProcess child : childList) {
			child.parent = null;
		}

		if (parent != null) {
			parent.exitStatusMap.put(processId, status);
		}

		activeProcess--;

		if (activeProcess == 0)
			Kernel.kernel.terminate();
		else
			UThread.finish();

		Lib.assertNotReached();
		return -1;
	}

	private int handleJoin(int pid, int addr) {
		UserProcess child = null;
		for (UserProcess p : childList) {
			if (p.processId == pid)
				child = p;
		}
		if (child == null)
			return -1;
		if (child.thread != null)
			child.thread.join();
		child.parent = null; // this line seems redundant; no one is going to
								// use it from now
		childList.remove(child);
		mapLock.acquire();
		if (!exitStatusMap.containsKey(child.processId)) {
			mapLock.release();
			return 0;
		}
		int status = exitStatusMap.get(child.processId);
		exitStatusMap.remove(child.processId);
		mapLock.release();

		if (status == UNEXPECTED_EXCEPTION) {
			return 0;
		}

		byte[] temp = Lib.bytesFromInt(status);
		writeVirtualMemory(addr, temp);
		// What if the above fails? return 1 or 0?
		// From syscall.h I think it is 1.
		return 1;
	}

	private int handleExec(int addr, int argc, int argv) {
		// makes sure that argc is not ridiculously long
		if (addr < 0 || argc < 0 || argv < 0 || argc > 65536)
			return -1;
		String file = readVirtualMemoryString(addr, 256);
		if (file == null || !file.toLowerCase().endsWith(".coff"))
			return -1;
		String[] arguments = new String[argc];
		// read those arguments from virtual address
		for (int i = 0; i < argc; i++) {
			byte[] tmp = new byte[4];
			int got = readVirtualMemory(argv + i * 4, tmp);
			if (got < 4)
				return -1;

			int argAddr = Lib.bytesToInt(tmp, 0);
			arguments[i] = readVirtualMemoryString(argAddr, 256);
			if (arguments[i] == null)
				return -1;
		}

		UserProcess child = UserProcess.newUserProcess();
		// Set the child's parent pointer first to avoid race condition
		child.parent = this;
		if (child.execute(file, arguments)) {
			childList.add(child);
			return child.processId;
		}

		return -1;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall
	 *            the syscall number.
	 * @param a0
	 *            the first syscall argument.
	 * @param a1
	 *            the second syscall argument.
	 * @param a2
	 *            the third syscall argument.
	 * @param a3
	 *            the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			handleException(UNKNOWN_SYSTEM_CALL);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;
		case UNKNOWN_SYSTEM_CALL:
			// ignore it
			break;
		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			handleExit(UNEXPECTED_EXCEPTION);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	private OpenFile[] fileList;
	private int processId;

	private LinkedList<UserProcess> childList = new LinkedList<UserProcess>();
	private UserProcess parent = null;
	private Map<Integer, Integer> exitStatusMap = new HashMap<Integer, Integer>();
	private Lock mapLock = new Lock();
	private UThread thread;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final int MAX_BUFFER_SIZE = 1 << 12;
	private byte[] BUFFER = new byte[MAX_BUFFER_SIZE];
	private static final int MAX_FILE_OPEN = 256;

	private static final int UNEXPECTED_EXCEPTION = -1234;
	private static final int UNKNOWN_SYSTEM_CALL = -1235;

	private static int processCounter = 1;
	private static final int ROOT_PROCESS = 1;

	/** The number of process that is active */
	protected static int activeProcess = 0;
}
