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
import org.apache.abdera.protocol.server.context.ResponseContextException;
import org.xcmis.restatom.AtomCMIS;
import org.xcmis.spi.CmisConstants;
import org.xcmis.spi.Connection;
import org.xcmis.spi.FilterNotValidException;
import org.xcmis.spi.InvalidArgumentException;
import org.xcmis.spi.ObjectNotFoundException;
import org.xcmis.spi.model.CmisObject;
import org.xcmis.spi.model.IncludeRelationships;
import org.xcmis.spi.model.ObjectParent;

import java.util.List;

/**
 * @author <a href="mailto:andrey.parfonov@exoplatform.com">Andrey Parfonov</a>
 * @version $Id: ParentsCollection.java 216 2010-02-12 17:19:50Z andrew00x $
 */
public class ParentsCollection extends CmisObjectCollection
{

   public ParentsCollection(Connection connection)
   {
      super(connection);
      setHref("/parents");
   }

   /**
    * {@inheritDoc}
    */
   public Iterable<CmisObject> getEntries(RequestContext request) throws ResponseContextException
   {
      // To process hierarchically structure override addFeedDetails(Feed, RequestContext) method.
      throw new UnsupportedOperationException("entries");
   }

   /**
    * {@inheritDoc}
    */
   public String getTitle(RequestContext request)
   {
      return "Object Parents";
   }

   /**
    * Adds the feed details.
    *
    * @param feed the feed
    * @param request the request
    * @throws ResponseContextException the response context exception
    */
   @Override
   protected void addFeedDetails(Feed feed, RequestContext request) throws ResponseContextException
   {
      try
      {
         boolean includeAllowableActions =
            getBooleanParameter(request, AtomCMIS.PARAM_INCLUDE_ALLOWABLE_ACTIONS, false);
         boolean includeRelativePathSegment =
            getBooleanParameter(request, AtomCMIS.PARAM_INCLUDE_RELATIVE_PATH_SEGMENT, false);
         String propertyFilter = request.getParameter(AtomCMIS.PARAM_FILTER);
         String renditionFilter = request.getParameter(AtomCMIS.PARAM_RENDITION_FILTER);
         IncludeRelationships includeRelationships;
         try
         {
            includeRelationships =
               request.getParameter(AtomCMIS.PARAM_INCLUDE_RELATIONSHIPS) == null
                  || request.getParameter(AtomCMIS.PARAM_INCLUDE_RELATIONSHIPS).length() == 0
                  ? IncludeRelationships.NONE : IncludeRelationships.fromValue(request
                     .getParameter(AtomCMIS.PARAM_INCLUDE_RELATIONSHIPS));
         }
         catch (IllegalArgumentException iae)
         {
            String msg = "Invalid parameter " + request.getParameter(AtomCMIS.PARAM_INCLUDE_RELATIONSHIPS);
            throw new ResponseContextException(msg, 400);
         }

         Connection connection = getConnection(request);
         String objectId = getId(request);

         CmisObject object =
            connection.getObject(objectId, false, IncludeRelationships.NONE, false, false, true,
               CmisConstants.BASE_TYPE_ID, null);

         switch (getBaseObjectType(object))
         {
            case FOLDER :
               CmisObject folderParent = connection.getFolderParent(objectId, true, propertyFilter);

               if (folderParent != null)
               {
                  // add cmisra:numItems
                  Element numItems = feed.addExtension(AtomCMIS.NUM_ITEMS);
                  numItems.setText("1"); // Folder always has only one parent.
                  Entry e = feed.addEntry();
                  IRI feedIri = new IRI(getFeedIriForEntry(folderParent, request));
                  addEntryDetails(request, e, feedIri, folderParent);
                  String name = object.getObjectInfo().getName();
                  if (name != null)
                  {
                     // add cmisra:relativePathSegment
                     Element pathSegment = e.addExtension(AtomCMIS.RELATIVE_PATH_SEGMENT);
                     pathSegment.setText(name);
                  }
               }
               break;
            default :
               List<ObjectParent> parents =
                  connection.getObjectParents(objectId, includeAllowableActions, includeRelationships,
                     includeRelativePathSegment, true, propertyFilter, renditionFilter);
               if (parents.size() > 0)
               {
                  // add cmisra:numItems
                  Element numItems = feed.addExtension(AtomCMIS.NUM_ITEMS);
                  numItems.setText(Integer.toString(parents.size()));

                  for (ObjectParent parent : parents)
                  {
                     Entry e = feed.addEntry();
                     IRI feedIri = new IRI(getFeedIriForEntry(parent.getObject(), request));
                     addEntryDetails(request, e, feedIri, parent.getObject());

                     if (parent.getRelativePathSegment() != null)
                     {
                        // add cmisra:relativePathSegment
                        Element pathSegment = e.addExtension(AtomCMIS.RELATIVE_PATH_SEGMENT);
                        pathSegment.setText(parent.getRelativePathSegment());
                     }
                  }
               }
               break;
         }
      }
      catch (FilterNotValidException fe)
      {
         throw new ResponseContextException(createErrorResponse(fe, 500));
      }
      catch (ObjectNotFoundException onfe)
      {
         throw new ResponseContextException(createErrorResponse(onfe, 404));
      }
      catch (InvalidArgumentException iae)
      {
         throw new ResponseContextException(createErrorResponse(iae, 400));
      }
      catch (Exception t)
      {
         throw new ResponseContextException(createErrorResponse(t, 500));
      }
   }

}
