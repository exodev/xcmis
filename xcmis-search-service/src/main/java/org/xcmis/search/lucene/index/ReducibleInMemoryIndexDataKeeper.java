/*
 * Copyright (C) 2009 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xcmis.search.lucene.index;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.xcmis.spi.utils.Logger;

/**
 * Created by The eXo Platform SAS.
 * 
 * @author <a href="mailto:Sergey.Kabashnyuk@gmail.com">Sergey Kabashnyuk</a>
 * @version $Id: ReducibleInMemoryIndexDataKeeper.java 2 2010-02-04 17:21:49Z andrew00x $
 */
public class ReducibleInMemoryIndexDataKeeper implements TransactionableLuceneIndexDataManager
{
   /**
    * Class logger.g
    */
   private static final Logger LOG = Logger.getLogger(ReducibleInMemoryIndexDataKeeper.class);

   /**
    * Index storage.
    */
   public final RAMDirectory indexDirectiry;

   public long lastModifedTime;

   /**
    * pending committed flag
    */
   boolean isPendingCommited;

   /**
    * Started flag.
    */
   boolean isStarted;

   /**
    * Stopped flag.
    */
   boolean isStoped;

   /**
    * Documents map
    */
   private final Map<String, Document> documentsBuffer;

   /**
    * Index storage.
    */
   private IndexReader indexReader;

   /**
    * Pending document buffer.
    */
   private final Map<String, Document> pendingDocumentsBuffer;

   /**
    * Data keeper transaction log.
    */
   private final TransactionLog transactionLog;

   /**
    * @param changes
    * @throws IndexException
    */
   public ReducibleInMemoryIndexDataKeeper(final LoggedIndexTransaction<Document> changes) throws IndexException
   {
      // this.changes = changes;
      this.indexDirectiry = new RAMDirectory();
      this.transactionLog = changes.getTransactionLog();
      this.isStarted = false;
      this.isPendingCommited = false;
      this.lastModifedTime = System.currentTimeMillis();

      this.documentsBuffer = new HashMap<String, Document>(changes.getAddedDocuments());
      this.pendingDocumentsBuffer = new HashMap<String, Document>(changes.getAddedDocuments());
      initDirectory();
   }

   public ReducibleInMemoryIndexDataKeeper(final RAMDirectory indexDirectiry,
      final Map<String, Document> documentsBuffer, final Map<String, Document> pendingDocumentsBuffer,
      final TransactionLog transactionLog)
   {
      this.indexDirectiry = indexDirectiry;
      this.documentsBuffer = documentsBuffer;
      this.pendingDocumentsBuffer = pendingDocumentsBuffer;
      this.transactionLog = transactionLog;
      this.isStarted = false;
      // this.changes = null;
      this.isPendingCommited = pendingDocumentsBuffer.size() == 0;
      this.lastModifedTime = System.currentTimeMillis();
   }

   /**
    * @return
    * @throws IndexException
    */
   public Directory getDirectory() throws IndexException
   {
      if (!isPendingCommited)
      {
         commitPending();
      }
      return indexDirectiry;
   }

   /**
    * {@inheritDoc}
    */
   public long getDirectorySize(final boolean includeInherited)
   {

      return this.indexDirectiry.sizeInBytes();
   }

   /**
    * {@inheritDoc}
    */
   public Document getDocument(final String uuid) throws IndexException
   {
      return this.documentsBuffer.get(uuid);
   }

   /**
    * @return the documentsCount
    */
   public long getDocumentCount()
   {
      return this.documentsBuffer.size();
   }

   /**
    * @return the documentsUuids
    */
   public Map<String, Document> getDocumentsBuffer()
   {
      return this.documentsBuffer;
   }

   /**
    * {@inheritDoc}
    * 
    * @throws IndexException
    */
   public IndexReader getIndexReader() throws IndexException
   {
      // TODO check is stated;
      if (this.documentsBuffer.size() < 1)
      {
         return null;
      }
      if (!this.isPendingCommited)
      {
         this.commitPending();
      }
      try
      {
         if (this.indexReader == null)
         {
            this.indexReader = IndexReader.open(this.indexDirectiry);
         }
         else if (!this.indexReader.isCurrent())
         {
            this.indexReader = this.indexReader.reopen();
         }
      }
      catch (final CorruptIndexException e)
      {
         throw new IndexException(e.getLocalizedMessage(), e);
      }
      catch (final IOException e)
      {
         // e.printStackTrace()
         throw new IndexException(e.getLocalizedMessage(), e);
      }
      return this.indexReader;
   }

   public long getLastModifedTime()
   {
      return this.lastModifedTime;
   }

   /**
    * @return the pendingDocumentsBuffer
    */
   public Map<String, Document> getPendingDocumentsBuffer()
   {
      return this.pendingDocumentsBuffer;
   }

   /**
    * {@inheritDoc}
    */
   public TransactionLog getTransactionLog()
   {
      return this.transactionLog;
   }

   /**
    * @return the isPendingCommited
    */
   public boolean isPendingCommited()
   {
      return this.isPendingCommited;
   }

   /**
    * @return the isStarted
    */
   public boolean isStarted()
   {
      return this.isStarted;
   }

   /**
    * @return the isStoped
    */
   public boolean isStoped()
   {
      return this.isStoped;
   }

   /**
    * {@inheritDoc}
    */
   public IndexTransactionModificationReport save(final IndexTransaction<Document> changes) throws IndexException,
      IndexTransactionException
   {
      // Accepting only deletes , and removing for updates.
      final Set<String> addedDocuments = new HashSet<String>();
      final Set<String> removedDocuments = new HashSet<String>();
      final Set<String> updatedDocuments = new HashSet<String>();

      try
      {
         // index already started
         synchronized (indexDirectiry)
         {
            final Set<String> removed = changes.getRemovedDocuments();
            // int numDoc = 0;
            IndexWriter writer = null;
            for (final String removedUuid : removed)
            {

               if (this.documentsBuffer.remove(removedUuid) != null)
               {

                  removedDocuments.add(removedUuid);
                  if (this.isPendingCommited || this.pendingDocumentsBuffer.remove(removedUuid) == null)
                  {
                     if (writer == null)
                     {
                        writer = new IndexWriter(this.indexDirectiry, 
                                new StandardAnalyzer(Version.LUCENE_35), MaxFieldLength.UNLIMITED);
                        //to avoid deadlock
                        writer.setMergeScheduler(new SerialMergeScheduler());
                     }
                     writer.deleteDocuments(new Term(FieldNames.UUID, removedUuid));
                  }
               }

            }

            if (writer != null)
            {
               writer.commit();
               writer.close();
               this.lastModifedTime = System.currentTimeMillis();
            }
         }

      }
      catch (final CorruptIndexException e)
      {
         throw new IndexException(e.getLocalizedMessage(), e);
      }
      catch (final IOException e)
      {
         throw new IndexException(e.getLocalizedMessage(), e);
      }

      return new IndexTransactionModificationReportImpl(addedDocuments, removedDocuments, updatedDocuments);
   }

   /**
    * {@inheritDoc}
    * 
    * @throws IndexException
    */
   public void start() throws IndexException
   {
      this.isStarted = true;

   }

   /**
    * {@inheritDoc}
    * 
    * @throws IndexException
    */
   public void stop() throws IndexException
   {
      this.indexDirectiry.close();
      this.isStoped = true;
   }

   /**
    * @throws CorruptIndexException
    * @throws LockObtainFailedException
    * @throws IOException
    */
   private void commitPending() throws IndexException
   {
      IndexWriter writer = null;
      try
      {
         if (this.pendingDocumentsBuffer.size() > 0)
         {
            synchronized (this.indexDirectiry)
            {

               writer = new IndexWriter(this.indexDirectiry, new StandardAnalyzer(Version.LUCENE_35), MaxFieldLength.UNLIMITED);
               for (final Entry<String, Document> addedDocument : this.pendingDocumentsBuffer.entrySet())
               {
                  writer.addDocument(addedDocument.getValue());

               }
               this.pendingDocumentsBuffer.clear();
               // write changes
               writer.commit();
               this.isPendingCommited = true;
               writer.close();
            }
         }
      }
      catch (final CorruptIndexException e)
      {
         throw new IndexException(e.getLocalizedMessage(), e);
      }
      catch (final LockObtainFailedException e)
      {
         throw new IndexException(e.getLocalizedMessage(), e);
      }
      catch (final IOException e)
      {
         throw new IndexException(e.getLocalizedMessage(), e);
      }
      finally
      {
         if (writer != null)
         {
            try
            {
               writer.close();
            }
            catch (CorruptIndexException e)
            {
               throw new IndexException(e.getLocalizedMessage(), e);
            }
            catch (IOException e)
            {
               throw new IndexException(e.getLocalizedMessage(), e);
            }
         }
      }
   }

   /**
    * @throws CorruptIndexException
    * @throws LockObtainFailedException
    * @throws IOException
    */
   private void initDirectory() throws IndexException
   {
      try
      {
         IndexWriter.MaxFieldLength fieldLength = new IndexWriter.MaxFieldLength(IndexWriter.DEFAULT_MAX_FIELD_LENGTH);
         IndexWriter iw = new IndexWriter(indexDirectiry, new SimpleAnalyzer(), true, fieldLength);
         iw.close();
      }
      catch (IOException e)
      {
         throw new IndexException(e.getLocalizedMessage(), e);
      }
   }
}
