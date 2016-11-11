package simpledb.buffer;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import simpledb.file.Block;
import simpledb.file.FileMgr;

/**
 * Manages the pinning and unpinning of buffers to blocks.
 * @author Edward Sciore
 *
 */
class BasicBufferMgr {
   private Queue<Buffer> bufferpool;
   private Map<Block, Buffer> bufferPoolMap;
   private Map<Buffer, Block> bufferToBlockMap;
   private int numAvailable;
   
   /**
    * Creates a buffer manager having the specified number 
    * of buffer slots.
    * This constructor depends on both the {@link FileMgr} and
    * {@link simpledb.log.LogMgr LogMgr} objects 
    * that it gets from the class
    * {@link simpledb.server.SimpleDB}.
    * Those objects are created during system initialization.
    * Thus this constructor cannot be called until 
    * {@link simpledb.server.SimpleDB#initFileAndLogMgr(String)} or
    * is called first.
    * @param numbuffs the number of buffer slots to allocate
    */
   BasicBufferMgr(int numbuffs) {
	  bufferPoolMap = new HashMap<Block, Buffer>();
	  bufferToBlockMap = new HashMap<Buffer, Block>();
	  bufferpool = new LinkedBlockingQueue<Buffer>(numbuffs);
      numAvailable = numbuffs;
      for (int i=0; i<numbuffs; i++)
         bufferpool.offer(new Buffer());
   }
   
   /**
    * Flushes the dirty buffers modified by the specified transaction.
    * @param txnum the transaction's id number
    */
   synchronized void flushAll(int txnum) {
      for (Buffer buff : bufferpool)
         if (buff.isModifiedBy(txnum))
        	 buff.flush();
   }
   
   /**
    * Pins a buffer to the specified block. 
    * If there is already a buffer assigned to that block
    * then that buffer is used;  
    * otherwise, an unpinned buffer from the pool is chosen.
    * Returns a null value if there are no available buffers.
    * @param blk a reference to a disk block
    * @return the pinned buffer
    */
   synchronized Buffer pin(Block blk) {
      Buffer buff = findExistingBuffer(blk);
      if (buff == null) {
         buff = chooseUnpinnedBuffer();
         if (buff == null)
            return null;
         buff.assignToBlock(blk);
         addToBlockToBufferMap(blk, buff);
      }
      if (!buff.isPinned())
         numAvailable--;
      buff.pin();
      return buff;
   }
   
   /**
    * Allocates a new block in the specified file, and
    * pins a buffer to it. 
    * Returns null (without allocating the block) if 
    * there are no available buffers.
    * @param filename the name of the file
    * @param fmtr a pageformatter object, used to format the new block
    * @return the pinned buffer
    */
   synchronized Buffer pinNew(String filename, PageFormatter fmtr) {
      Buffer buff = chooseUnpinnedBuffer();
      if (buff == null)
         return null;
      Block newBlk = buff.assignToNew(filename, fmtr);
      addToBlockToBufferMap(newBlk, buff);
      pushBufferToEndOfPool(buff);
      numAvailable--;
      buff.pin();
      return buff;
   }

   /**
    * Unpins the specified buffer.
    * @param buff the buffer to be unpinned
    */
   synchronized void unpin(Buffer buff) {
      buff.unpin();
      if (!buff.isPinned())
         numAvailable++;
   }
   
   /**
    * Returns the number of available (i.e. unpinned) buffers.
    * @return the number of available buffers
    */
   int available() {
      return numAvailable;
   }
   
   private Buffer findExistingBuffer(Block blk) {
	  return bufferPoolMap.get(blk);
   }
   
   private Buffer chooseUnpinnedBuffer() {
      for (Buffer buff : bufferpool)
         if (!buff.isPinned())
        	 return buff;
      return null;
   }
   
   private void pushBufferToEndOfPool(Buffer buff) {
	   if(bufferpool.remove(buff))
		   bufferpool.add(buff);
   }
   
   private void addToBlockToBufferMap(Block blk, Buffer buff) {
	   Block oldBlk = bufferToBlockMap.get(buff);
	   if(oldBlk!=null){
		   bufferPoolMap.remove(oldBlk);
	   }
	   bufferPoolMap.put(blk, buff);
	   bufferToBlockMap.put(buff, blk);
   }
}
