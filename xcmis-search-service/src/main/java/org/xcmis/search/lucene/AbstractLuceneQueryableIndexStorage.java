/*
 * Copyright (C) 2010 eXo Platform SAS.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.xcmis.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.Validate;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.xcmis.search.VisitException;
import org.xcmis.search.Visitors;
import org.xcmis.search.config.IndexConfiguration;
import org.xcmis.search.config.IndexConfigurationException;
import org.xcmis.search.config.SearchServiceConfiguration;
import org.xcmis.search.content.ContentEntry;
import org.xcmis.search.content.command.InvocationContext;
import org.xcmis.search.content.command.index.ModifyIndexCommand;
import org.xcmis.search.content.command.query.ExecuteSelectorCommand;
import org.xcmis.search.content.interceptors.QueryableIndexStorage;
import org.xcmis.search.lucene.content.VirtualTableResolver;
import org.xcmis.search.lucene.index.FieldNames;
import org.xcmis.search.lucene.index.IndexException;
import org.xcmis.search.lucene.index.IndexTransactionException;
import org.xcmis.search.lucene.index.LuceneIndexTransaction;
import org.xcmis.search.lucene.index.LuceneIndexer;
import org.xcmis.search.lucene.search.UUIDFieldSelector;
import org.xcmis.search.model.Limit;
import org.xcmis.search.model.constraint.Constraint;
import org.xcmis.search.model.operand.FullTextSearchScore;
import org.xcmis.search.model.operand.Length;
import org.xcmis.search.model.operand.LowerCase;
import org.xcmis.search.model.operand.NodeDepth;
import org.xcmis.search.model.operand.NodeLocalName;
import org.xcmis.search.model.operand.NodeName;
import org.xcmis.search.model.operand.PropertyValue;
import org.xcmis.search.model.operand.UpperCase;
import org.xcmis.search.model.ordering.Order;
import org.xcmis.search.model.ordering.Ordering;
import org.xcmis.search.result.ScoredRow;
import org.xcmis.search.value.NameConverter;
import org.xcmis.search.value.PathSplitter;
import org.xcmis.spi.utils.Logger;

/**
 * Base implementation of Lucene based {@link QueryableIndexStorage}.
 * 
 */
public abstract class AbstractLuceneQueryableIndexStorage extends QueryableIndexStorage
{

   /**
    * Class logger.
    */
   private static final Logger LOG = Logger.getLogger(AbstractLuceneQueryableIndexStorage.class);

   /**
    * The upper limit for the initial fetch size.
    */
   protected static final int MAX_FETCH_SIZE = 32 * 1024;

   /**
    * Convert one Sting name to other String name.
    */
   protected final NameConverter nameConverter;

   /**
    * Split path string to names.
    */
   protected final PathSplitter pathSplitter;

   /**
    * Reselve selector names to lucene querys.
    */
   protected final VirtualTableResolver tableResolver;

   /**
    * Node indexer.
    */
   protected LuceneIndexer nodeIndexer;

   /**
    * Index configuration.
    */
   protected IndexConfiguration indexConfuguration;

   /**
    * @param serviceConfiguration
    * @throws IndexException
    * @throws IndexConfigurationException
    * @throws org.xcmis.search.lucene.index.IndexException
    */
   public AbstractLuceneQueryableIndexStorage(SearchServiceConfiguration serviceConfiguration) throws IndexException
   {
      super(serviceConfiguration);
      Validate.notNull(serviceConfiguration.getTableResolver(),
         "The TableResolver may not be null in SearchServiceConfiguration");
      Validate.notNull(serviceConfiguration.getNameConverter(),
         "The NameConverter may not be null in SearchServiceConfiguration");
      Validate.notNull(serviceConfiguration.getPathSplitter(),
         "The PathSplitter may not be null in SearchServiceConfiguration");
      Validate.notNull(serviceConfiguration.getIndexConfuguration(),
         "The TableResolver  argument may not be null in SearchServiceConfiguration");
      Validate.notNull(serviceConfiguration.getIndexConfuguration().getRootParentUuid(),
         "The RootParentUuid  argument may not be null in IndexConfiguration");
      Validate.notNull(serviceConfiguration.getIndexConfuguration().getRootUuid(),
         "The RootUuid may not be null in IndexConfiguration");
      Validate.notNull(serviceConfiguration.getIndexConfuguration().getTikaConfiguration(),
         "The TikaConfiguration may not be null in IndexConfiguration");

      this.tableResolver = serviceConfiguration.getTableResolver();
      this.nameConverter = serviceConfiguration.getNameConverter();
      this.pathSplitter = serviceConfiguration.getPathSplitter();
      this.indexConfuguration = serviceConfiguration.getIndexConfuguration();

      this.nodeIndexer = new LuceneIndexer(indexConfuguration);

   }

   public Query getConstrainQuery(Constraint constraint, Map<String, Object> bindVariablesValues)
      throws VisitException, IndexException
   {
      LuceneQueryBuilder luceneQueryBuilder =
         new LuceneQueryBuilder(getIndexReader(), nameConverter, pathSplitter, bindVariablesValues, indexConfuguration);
      Visitors.visit(constraint, luceneQueryBuilder);
      return luceneQueryBuilder.getQuery();
   }

   /**
    * @see org.xcmis.search.content.interceptors.QueryableIndexStorage#visitExecuteSelectorCommand(org.xcmis.search.content.command.InvocationContext,
    *      org.xcmis.search.content.command.query.ExecuteSelectorCommand)
    */
   @Override
   public Object visitExecuteSelectorCommand(InvocationContext ctx, ExecuteSelectorCommand command) throws Throwable
   {

      List<ScoredRow> resultNodes = new ArrayList<ScoredRow>();
      Query query = (Query)ctx.getTableResolver().resolve(command.getSelector().getName(), true);
      if (command.getConstrains().size() > 0)
      {
         BooleanQuery booleanQuery = new BooleanQuery();

         for (Constraint constrain : command.getConstrains())
         {
            booleanQuery.add(getConstrainQuery(constrain, command.getBindVariablesValues()), Occur.MUST);
         }

         booleanQuery.add(query, Occur.MUST);
         query = booleanQuery;
      }
      // Open writer

      IndexSearcher searcher = null;
      try
      {
         // get result
         IndexReader indexReader = getIndexReader();
         if (indexReader != null)
         {
            searcher = new IndexSearcher(indexReader);

            // query
            Limit limit = command.getLimit();
            int hits = Math.min(MAX_FETCH_SIZE, limit.getOffset() + limit.getRowLimit());
            TopFieldDocs topDocs = searcher.search(query, null, hits, getSort(command.getOrderings()));

            resultNodes = new LinkedList<ScoredRow>();
            for (int i = limit.getOffset(); i < topDocs.scoreDocs.length; i++)
            {
               // get identifiers
               final Document doc = searcher.doc(topDocs.scoreDocs[i].doc, new UUIDFieldSelector());
               final String id = doc.get(FieldNames.UUID);
               resultNodes.add(new ScoredRow(command.getAlias().getName(), id, topDocs.scoreDocs[i].score));
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
      finally
      {
         try
         {
            if (searcher != null)
            {
               searcher.close();
            }
         }
         catch (final IOException e)
         {
            throw new IndexException(e.getLocalizedMessage(), e);
         }
      }
      return resultNodes;
   }

   /**
    * @see org.xcmis.search.content.interceptors.QueryableIndexStorage#visitModifyIndexCommand(org.xcmis.search.content.command.InvocationContext,
    *      org.xcmis.search.content.command.index.ModifyIndexCommand)
    */
   @Override
   public Object visitModifyIndexCommand(InvocationContext ctx, ModifyIndexCommand command) throws Throwable
   {
      Map<String, Document> addedDocuments = new HashMap<String, Document>();
      // indexing content
      for (ContentEntry entry : command.getAddedDocuments())
      {
         addedDocuments.put(entry.getIdentifier(), nodeIndexer.createDocument(entry));
      }

      LuceneIndexTransaction indexTransaction =
         new LuceneIndexTransaction(addedDocuments, command.getDeletedDocuments());

      return save(indexTransaction);
   }

   /**
    * Different lucene storage's should override this method.
    */
   protected abstract IndexReader getIndexReader() throws IndexException;

   /**
    * @param indexTransaction
    * @return
    * @throws IndexTransactionException
    * @throws IndexException
    */
   protected abstract Object save(LuceneIndexTransaction indexTransaction) throws IndexException,
      IndexTransactionException;

   /**
    * Return lucene sorter by list of orderings.
    * 
    * @param list
    * @return
    * @throws VisitException
    */
   private Sort getSort(List<Ordering> list) throws VisitException
   {
      if (list.size() > 0)
      {
         SortField[] fields = new SortField[list.size()];
         SortFieldVisitor sortVisitor = new SortFieldVisitor();
         int i = 0;
         for (Ordering ordering : list)
         {
            Visitors.visitAll(ordering, sortVisitor);
            fields[i++] = sortVisitor.getSortField();
         }
         return new Sort(fields);

      }
      return new Sort();
   }

   private class SortFieldVisitor extends Visitors.AbstractModelVisitor
   {

      private Order order;

      private SortField sortField;

      public SortField getSortField()
      {
         return sortField;
      }

      /**
       * @see org.xcmis.search.Visitors.AbstractModelVisitor#visit(org.xcmis.search.model.operand.FullTextSearchScore)
       */
      @Override
      public void visit(FullTextSearchScore node) throws VisitException
      {
         //By default result sorted score descendant
         sortField = new SortField(null, SortField.SCORE, order == Order.ASCENDING);
      }

      /**
       * @see org.xcmis.search.Visitors.AbstractModelVisitor#visit(org.xcmis.search.model.operand.Length)
       */
      @Override
      public void visit(Length node) throws VisitException
      {
         throw new NotImplementedException();
      }

      /**
       * @see org.xcmis.search.Visitors.AbstractModelVisitor#visit(org.xcmis.search.model.operand.LowerCase)
       */
      @Override
      public void visit(LowerCase node) throws VisitException
      {
         throw new NotImplementedException();
      }

      /**
       * @see org.xcmis.search.Visitors.AbstractModelVisitor#visit(org.xcmis.search.model.operand.NodeDepth)
       */
      @Override
      public void visit(NodeDepth depth) throws VisitException
      {
         throw new NotImplementedException();
      }

      /**
       * @see org.xcmis.search.Visitors.AbstractModelVisitor#visit(org.xcmis.search.model.operand.NodeLocalName)
       */
      @Override
      public void visit(NodeLocalName node) throws VisitException
      {
         super.visit(node);
      }

      /**
       * @see org.xcmis.search.Visitors.AbstractModelVisitor#visit(org.xcmis.search.model.operand.NodeName)
       */
      @Override
      public void visit(NodeName node) throws VisitException
      {
         throw new NotImplementedException();
      }

      /**
       * 
       * @see org.xcmis.search.Visitors.AbstractModelVisitor#visit(org.xcmis.search.model.ordering.Ordering)
       */
      public void visit(Ordering node) throws VisitException
      {
         order = node.getOrder();
      }

      /**
       * @see org.xcmis.search.Visitors.AbstractModelVisitor#visit(org.xcmis.search.model.operand.PropertyValue)
       */
      @Override
      public void visit(PropertyValue node) throws VisitException
      {
        //AUTO mode was removed since version 3.0. Therefore we will used STRING type as sort type.
         sortField =
            new SortField(FieldNames.createPropertyFieldName(node.getPropertyName()), 
                    SortField.STRING, order == Order.DESCENDING);
      }

      /**
       * @see org.xcmis.search.Visitors.AbstractModelVisitor#visit(org.xcmis.search.model.operand.UpperCase)
       */
      @Override
      public void visit(UpperCase node) throws VisitException
      {
         throw new NotImplementedException();
      }
   }
}
