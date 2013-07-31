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

import org.apache.abdera.factory.Factory;
import org.apache.abdera.i18n.iri.IRI;
import org.apache.abdera.model.Element;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.model.Link;
import org.apache.abdera.protocol.server.RequestContext;
import org.apache.abdera.protocol.server.context.ResponseContextException;
import org.xcmis.restatom.AtomCMIS;
import org.xcmis.restatom.AtomUtils;
import org.xcmis.spi.CmisConstants;
import org.xcmis.spi.Connection;
import org.xcmis.spi.InvalidArgumentException;
import org.xcmis.spi.ItemsTree;
import org.xcmis.spi.TypeNotFoundException;
import org.xcmis.spi.model.TypeDefinition;

import java.util.Calendar;
import java.util.List;

/**
 * @author <a href="mailto:andrey.parfonov@exoplatform.com">Andrey Parfonov</a>
 * @version $Id: TypesDescendantsCollection.java 216 2010-02-12 17:19:50Z
 *          andrew00x $
 */
public class TypesDescendantsCollection extends CmisTypeCollection
{

   public TypesDescendantsCollection(Connection connection)
   {
      super(connection);
      setHref("/typedescendants");
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Iterable<TypeDefinition> getEntries(RequestContext request) throws ResponseContextException
   {
      // To process hierarchically structure override addFeedDetails(Feed, RequestContext) method.
      throw new UnsupportedOperationException("entries");
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void addFeedDetails(Feed feed, RequestContext request) throws ResponseContextException
   {
      try
      {
         String typeId = request.getTarget().getParameter(AtomCMIS.PARAM_TYPE_ID);
         boolean includePropertyDefinitions =
            getBooleanParameter(request, AtomCMIS.PARAM_INCLUDE_PROPERTY_DEFINITIONS, false);
         int depth = getIntegerParameter(request, AtomCMIS.PARAM_DEPTH, CmisConstants.DEPTH);

         Connection connection = getConnection(request);

         List<ItemsTree<TypeDefinition>> descendants =
            connection.getTypeDescendants(typeId, depth, includePropertyDefinitions);

         String down = getTypeChildrenLink(typeId, request);
         feed.addLink(down, AtomCMIS.LINK_DOWN, AtomCMIS.MEDIATYPE_ATOM_FEED, null, null, -1);

         if (typeId != null)
         {
            String typeLink = getObjectTypeLink(typeId, request);
            feed.addLink(typeLink, AtomCMIS.LINK_VIA, AtomCMIS.MEDIATYPE_ATOM_ENTRY, null, null, -1);

            TypeDefinition type = connection.getTypeDefinition(typeId);
            String parentType = type.getParentId();
            if (parentType != null)
            {
               String parent = getObjectTypeLink(parentType, request);
               feed.addLink(parent, AtomCMIS.LINK_UP, AtomCMIS.MEDIATYPE_ATOM_ENTRY, null, null, -1);
            }
         }
         for (ItemsTree<TypeDefinition> typeContainer : descendants)
         {
            Entry e = feed.addEntry();
            IRI feedIri = new IRI(getFeedIriForEntry(typeContainer.getContainer(), request));
            addEntryDetails(request, e, feedIri, typeContainer.getContainer());
            if (typeContainer.getChildren() != null && !typeContainer.getChildren().isEmpty())
            {
               addChildren(e, typeContainer.getChildren(), feedIri, request);
            }
         }
      }
      catch (InvalidArgumentException iae)
      {
         throw new ResponseContextException(createErrorResponse(iae, 400));
      }
      catch (TypeNotFoundException tnfe)
      {
         throw new ResponseContextException(createErrorResponse(tnfe, 404));
      }
      catch (Exception t)
      {
         throw new ResponseContextException(createErrorResponse(t, 500));
      }
   }

   /**
    * Adds the children.
    *
    * @param entry the entry
    * @param children the children
    * @param feedIri the feed iri
    * @param request the request
    *
    * @throws ResponseContextException the response context exception
    */
   protected void addChildren(Entry entry, List<ItemsTree<TypeDefinition>> children, IRI feedIri, RequestContext request)
      throws ResponseContextException
   {
      Element childrenElement = entry.addExtension(AtomCMIS.CHILDREN);
      // In this case entry is parent for feed, so use info from entry for new feed.
      String entryId = entry.getId().toString();
      Feed childFeed = request.getAbdera().getFactory().newFeed(childrenElement);
      childFeed.setId("ch:" + entryId);
      childFeed.setTitle("Type Children");
      childFeed.addAuthor(entry.getAuthor());
      childFeed.setUpdated(entry.getUpdated());

      // Copy some links from entry.
      List<Link> links =
         entry.getLinks(AtomCMIS.LINK_SERVICE, AtomCMIS.LINK_SELF, AtomCMIS.LINK_DOWN,
            AtomCMIS.LINK_CMIS_TYPEDESCENDANTS, AtomCMIS.LINK_UP);
      for (Link l : links)
      {
         childFeed.addLink((Link)l.clone());
      }

      childFeed.addLink(getObjectTypeLink(entryId, request), AtomCMIS.LINK_VIA, AtomCMIS.MEDIATYPE_ATOM_ENTRY, null,
         null, -1);

      // add cmisra:numItems
      Element numItems = request.getAbdera().getFactory().newElement(AtomCMIS.NUM_ITEMS, childrenElement);
      numItems.setText(Integer.toString(children.size()));

      for (ItemsTree<TypeDefinition> typeContainer : children)
      {
         Entry ch = entry.getFactory().newEntry(childrenElement);
         addEntryDetails(request, ch, feedIri, typeContainer.getContainer());
         if (typeContainer.getChildren() != null && typeContainer.getChildren().size() > 0)
         {
            addChildren(ch, typeContainer.getChildren(), feedIri, request);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getTitle(RequestContext request)
   {
      return "Type Descendants";
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected Feed createFeedBase(RequestContext request) throws ResponseContextException
   {
      Factory factory = request.getAbdera().getFactory();
      Feed feed = factory.newFeed();
      feed.setId(getId(request));
      feed.setTitle(getTitle(request));
      feed.addAuthor(getAuthor(request));
      feed.setUpdated(AtomUtils.getAtomDate(Calendar.getInstance()));
      feed.addLink(getServiceLink(request), AtomCMIS.LINK_SERVICE, AtomCMIS.MEDIATYPE_ATOM_SERVICE, null, null, -1);
      return feed;
   }
}
