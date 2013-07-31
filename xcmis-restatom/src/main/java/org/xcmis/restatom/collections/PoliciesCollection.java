/**
 * Copyright (C) 2010 eXo Platform SAS.
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

package org.xcmis.restatom.collections;

import org.apache.abdera.i18n.iri.IRI;
import org.apache.abdera.model.Element;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.protocol.server.RequestContext;
import org.apache.abdera.protocol.server.ResponseContext;
import org.apache.abdera.protocol.server.TargetType;
import org.apache.abdera.protocol.server.context.EmptyResponseContext;
import org.apache.abdera.protocol.server.context.ResponseContextException;
import org.xcmis.restatom.AtomCMIS;
import org.xcmis.restatom.abdera.ObjectTypeElement;
import org.xcmis.spi.CmisConstants;
import org.xcmis.spi.Connection;
import org.xcmis.spi.ConstraintException;
import org.xcmis.spi.FilterNotValidException;
import org.xcmis.spi.InvalidArgumentException;
import org.xcmis.spi.ObjectNotFoundException;
import org.xcmis.spi.model.CmisObject;
import org.xcmis.spi.model.IncludeRelationships;
import org.xcmis.spi.model.Property;
import org.xcmis.spi.model.impl.IdProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:andrey.parfonov@exoplatform.com">Andrey Parfonov</a>
 * @version $Id: PoliciesCollection.java 2487 2009-07-31 14:14:34Z andrew00x $
 */
public class PoliciesCollection extends CmisObjectCollection
{

   public PoliciesCollection(Connection connection)
   {
      super(connection);
      setHref("/policies");
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void addFeedDetails(Feed feed, RequestContext request) throws ResponseContextException
   {
      try
      {
         String propertyFilter = request.getParameter(AtomCMIS.PARAM_FILTER);
         int maxItems = getIntegerParameter(request, AtomCMIS.PARAM_MAX_ITEMS, CmisConstants.MAX_ITEMS);
         int skipCount = getIntegerParameter(request, AtomCMIS.PARAM_SKIP_COUNT, CmisConstants.SKIP_COUNT);

         Connection connection = getConnection(request);
         String objectId = getId(request);

         List<CmisObject> list = connection.getAppliedPolicies(objectId, true, propertyFilter);
         if (list.size() > 0)
         {
            // add cmisra:numItems
            Element numItems = feed.addExtension(AtomCMIS.NUM_ITEMS);
            numItems.setText(Integer.toString(list.size()));

            //Paging inks
            addPageLinks(objectId, feed, "policies", maxItems, skipCount, list.size(), (skipCount + maxItems) < list
               .size(), request);

            for (CmisObject one : list)
            {
               Entry e = feed.addEntry();
               IRI feedIri = new IRI(getFeedIriForEntry(one, request));
               addEntryDetails(request, e, feedIri, one);
            }
         }
      }
      catch (FilterNotValidException fe)
      {
         throw new ResponseContextException(createErrorResponse(fe, 400));
      }
      catch (ObjectNotFoundException onfe)
      {
         throw new ResponseContextException(createErrorResponse(onfe, 404));
      }
      catch (Exception t)
      {
         throw new ResponseContextException(createErrorResponse(t, 500));
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ResponseContext postEntry(RequestContext request)
   {
      Entry entry;
      String objectId;

      try
      {
         entry = getEntryFromRequest(request);
         objectId = getId(request);
      }
      catch (ResponseContextException rce)
      {
         return rce.getResponseContext();
      }

      ObjectTypeElement objectElement = entry.getFirstChild(AtomCMIS.OBJECT);
      boolean hasCMISElement = objectElement != null;
      CmisObject object = hasCMISElement ? objectElement.getObject() : new CmisObject();
      updatePropertiesFromEntry(object, entry);
      String policyId = null;
      if (hasCMISElement)
      {
         for (Property<?> p : object.getProperties().values())
         {
            String pName = p.getId();
            if (CmisConstants.OBJECT_ID.equals(pName))
            {
               policyId = ((IdProperty)p).getValues().get(0);
            }
         }
      }

      try
      {
         Connection connection = getConnection(request);
         // apply policy
         if (policyId != null)
         {
            connection.applyPolicy(policyId, objectId);
         }
         entry = request.getAbdera().getFactory().newEntry();
         // updated object
         addEntryDetails(request, entry, request.getResolvedUri(), connection.getObject(policyId, true,
            IncludeRelationships.BOTH, true, true, true, null, null));

         Map<String, String> params = new HashMap<String, String>();
         String link = request.absoluteUrlFor(TargetType.ENTRY, params);
         return buildCreateEntryResponse(link, entry);
      }
      catch (ConstraintException cve)
      {
         return createErrorResponse(cve, 409);
      }
      catch (ObjectNotFoundException onfe)
      {
         return createErrorResponse(onfe, 404);
      }
      catch (InvalidArgumentException iae)
      {
         return createErrorResponse(iae, 400);
      }
      catch (ResponseContextException rce)
      {
         return rce.getResponseContext();
      }
      catch (FilterNotValidException fae)
      {
         return createErrorResponse(fae, 400);
      }
      catch (Exception t)
      {
         return createErrorResponse(t, 500);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ResponseContext deleteEntry(RequestContext request)
   {
      Entry entry;
      String objectId;
      try
      {
         entry = getEntryFromRequest(request);
         objectId = getId(request);
      }
      catch (ResponseContextException rce)
      {
         return createErrorResponse(rce, 400);
      }

      ObjectTypeElement objectElement = entry.getFirstChild(AtomCMIS.OBJECT);
      CmisObject object = objectElement.getObject();

      String policyId = null;

      for (Property<?> p : object.getProperties().values())
      {
         String pName = p.getId();
         if (pName.equals(CmisConstants.OBJECT_ID))
         {
            policyId = ((IdProperty)p).getValues().get(0);
         }
      }
      try
      {
         Connection connection = getConnection(request);
         if (policyId != null)
         {
            connection.removePolicy(policyId, objectId);
         }
         ResponseContext response = new EmptyResponseContext(200);
         return response;
      }
      catch (ConstraintException cve)
      {
         return createErrorResponse(cve, 409);
      }
      catch (ObjectNotFoundException onfe)
      {
         return createErrorResponse(onfe, 404);
      }
      catch (InvalidArgumentException iae)
      {
         return createErrorResponse(iae, 400);
      }
      catch (Exception t)
      {
         return createErrorResponse(t, 500);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Iterable<CmisObject> getEntries(RequestContext request) throws ResponseContextException
   {
      // To process hierarchically structure override addFeedDetails(Feed, RequestContext) method.
      throw new UnsupportedOperationException("policies");
   }

   /**
    * {@inheritDoc}
    */
   public String getTitle(RequestContext request)
   {
      return "Policies";
   }
}
