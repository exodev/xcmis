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

package org.xcmis.restatom;

import org.apache.abdera.factory.Factory;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Link;
import org.apache.abdera.model.Service;
import org.apache.abdera.model.Workspace;
import org.apache.abdera.parser.stax.FOMExtensibleElement;
import org.apache.abdera.protocol.server.CollectionInfo;
import org.apache.abdera.protocol.server.RequestContext;
import org.apache.abdera.protocol.server.ResponseContext;
import org.apache.abdera.protocol.server.WorkspaceInfo;
import org.apache.abdera.protocol.server.context.BaseResponseContext;
import org.apache.abdera.protocol.server.context.ResponseContextException;
import org.apache.abdera.protocol.server.servlet.ServletRequestContext;
import org.apache.commons.fileupload.FileItem;
import org.xcmis.restatom.abdera.AccessControlEntryTypeElement;
import org.xcmis.restatom.abdera.AllowableActionsElement;
import org.xcmis.restatom.abdera.RepositoryInfoTypeElement;
import org.xcmis.restatom.abdera.UriTemplateTypeElement;
import org.xcmis.restatom.collections.AbstractCmisCollection;
import org.xcmis.restatom.types.CmisUriTemplateType;
import org.xcmis.spi.CmisRegistry;
import org.xcmis.spi.Connection;
import org.xcmis.spi.InvalidArgumentException;
import org.xcmis.spi.ObjectNotFoundException;
import org.xcmis.spi.UpdateConflictException;
import org.xcmis.spi.model.AccessControlEntry;
import org.xcmis.spi.model.AccessControlPropagation;
import org.xcmis.spi.model.AllowableActions;
import org.xcmis.spi.model.CapabilityChanges;
import org.xcmis.spi.model.RepositoryCapabilities;
import org.xcmis.spi.model.RepositoryInfo;
import org.xcmis.spi.model.RepositoryShortInfo;
import org.xcmis.spi.model.UnfileObject;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.activation.MimeTypeParseException;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.ResponseBuilder;

/**
 * @author <a href="mailto:andrey.parfonov@exoplatform.com">Andrey Parfonov</a>
 * @version $Id: AtomCmisService.java 216 2010-02-12 17:19:50Z andrew00x $
 */
@Path(AtomCMIS.CMIS_REST_RESOURCE_PATH)
public class AtomCmisService
{

   /** The provider. */
   protected ProviderImpl provider;

   /**
    * Instantiates a new atom cmis service.
    */
   public AtomCmisService(ProviderImpl provider)
   {
      this.provider = provider;
   }

   @PUT
   @Path("{repositoryId}/objacl/{objectId}")
   @Produces("application/cmisacl+xml")
   public Response addACL(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId,
      @PathParam("objectId") String objectId)
   {
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         RequestContext request = initRequestContext(httpRequest);
         Document<?> doc = request.getDocument();
         List<AccessControlEntryTypeElement> listEl = doc.getRoot().getElements();
         List<AccessControlEntry> listACE = new ArrayList<AccessControlEntry>();
         for (AccessControlEntryTypeElement el : listEl)
         {
            listACE.add(el.getACE());
         }
         List<AccessControlEntry> removeACL = new ArrayList<AccessControlEntry>();
         connection.applyACL(objectId, listACE, removeACL, AccessControlPropagation.REPOSITORYDETERMINED);
         List<AccessControlEntry> list = connection.getACL(objectId, false);
         FOMExtensibleElement accessControlListTypeElement =
            AbderaFactory.getInstance().getFactory().newElement(AtomCMIS.ACL);
         for (AccessControlEntry accessControlEntry : list)
         {
            AccessControlEntryTypeElement ace = accessControlListTypeElement.addExtension(AtomCMIS.PERMISSION);
            ace.build(accessControlEntry);
         }
         //return Response.status(201).build();
         return Response.ok(accessControlListTypeElement).header(HttpHeaders.CACHE_CONTROL, "no-cache").build();
      }
      catch (IOException io)
      {
         throw new WebApplicationException(io, createErrorResponse(io, 500));
      }
      catch (Exception t)
      {
         throw new WebApplicationException(t, createErrorResponse(t, 500));
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }

   }

   @POST
   @Path("{repositoryId}/types")
   @RolesAllowed({"administrators"})
   public Response addType(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return createItem(repositoryId, httpRequest);
   }

   @POST
   @Path("{repositoryId}/policies/{objectId}")
   @Produces("application/atom+xml;type=entry")
   public Response applyPolicy(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return createItem(repositoryId, httpRequest);
   }

   @POST
   @Path("{repositoryId}/checkedout")
   public Response checkOut(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
      throws Exception
   {
      return createItem(repositoryId, httpRequest);
   }

   @POST
   @Path("{repositoryId}/children/{folderId}")
   @Produces("application/atom+xml;type=entry")
   public Response createChild(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return createItem(repositoryId, httpRequest);
   }

   @POST
   @Path("{repositoryId}/object/{folderId}")
   @Produces("application/atom+xml;type=entry")
   public Response createChildObj(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      // Found some clients those use direct object (folder) link for adding child.
      return createItem(repositoryId, httpRequest);
   }

   @POST
   @Path("{repositoryId}/relationships/{objectId}")
   @Produces("application/atom+xml;type=entry")
   public Response createRelationship(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      return createItem(repositoryId, httpRequest);
   }

   @DELETE
   @Path("{repositoryId}/file/{objectId}")
   public Response deleteContentStream(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      RequestContext request = initRequestContext(httpRequest);
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         AbstractCmisCollection<?> collection = getCollection(request, connection);
         ResponseContext abderaResponse = collection.deleteMedia(request);
         return Response.status(abderaResponse.getStatus()).entity(abderaResponse).build();
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   @DELETE
   @Path("{repositoryId}/object/{objectId}")
   public Response deleteObject(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return deleteItem(repositoryId, httpRequest);
   }

   @DELETE
   @Path("{repositoryId}/foldertree/{folderId}")
   public Response deleteFolderTree(@PathParam("repositoryId") String repositoryId,
      @PathParam("folderId") String folderId, @QueryParam("unfileObject") String unfileNonfolderObjects,
      @DefaultValue("false") @QueryParam("continueOnFailure") boolean continueOnFailure)
   {
      return deleteDescendants(repositoryId, folderId, unfileNonfolderObjects, continueOnFailure);
   }

   @DELETE
   @Path("{repositoryId}/descendants/{folderId}")
   public Response deleteDescendants(@PathParam("repositoryId") String repositoryId,
      @PathParam("folderId") String folderId, @QueryParam("unfileObject") String unfileNonfolderObjects,
      @DefaultValue("false") @QueryParam("continueOnFailure") boolean continueOnFailure)
   {
      UnfileObject unfileObject;
      try
      {
         unfileObject =
            unfileNonfolderObjects == null ? UnfileObject.DELETE : UnfileObject.fromValue(unfileNonfolderObjects);
      }
      catch (IllegalArgumentException e)
      {
         throw new IllegalArgumentException("Unsupported 'unfileObject' attribute: " + unfileNonfolderObjects);
      }
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         Boolean deleteAllVersions = true; // TODO : backend (JCR) not support removing just one version.
         connection.deleteTree(folderId, deleteAllVersions, unfileObject, continueOnFailure);
         return Response.noContent().build();
      }
      catch (UpdateConflictException uce)
      {
         throw new WebApplicationException(uce, createErrorResponse(uce, 409));
      }
      catch (ObjectNotFoundException onfe)
      {
         throw new WebApplicationException(onfe, createErrorResponse(onfe, 404));
      }
      catch (InvalidArgumentException iae)
      {
         throw new WebApplicationException(iae, createErrorResponse(iae, 400));
      }
      catch (Exception t)
      {
         throw new WebApplicationException(t, createErrorResponse(t, 500));
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   @DELETE
   @Path("{repositoryId}/types/{typeId}")
   @RolesAllowed({"administrators"})
   public Response deleteType(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return deleteItem(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/objacl/{objectId}")
   @Produces("application/cmisacl+xml")
   public Response getACL(@PathParam("repositoryId") String repositoryId, @PathParam("objectId") String objectId,
      @DefaultValue("true") @QueryParam("onlyBasicPermissions") boolean onlyBasicPermissions)
   {
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         List<AccessControlEntry> list = connection.getACL(objectId, onlyBasicPermissions);
         FOMExtensibleElement accessControlListTypeElement =
            AbderaFactory.getInstance().getFactory().newElement(AtomCMIS.ACL);
         for (AccessControlEntry accessControlEntry : list)
         {
            AccessControlEntryTypeElement ace = accessControlListTypeElement.addExtension(AtomCMIS.PERMISSION);
            ace.build(accessControlEntry);
         }
         return Response.ok(accessControlListTypeElement).header(HttpHeaders.CACHE_CONTROL, "no-cache").build();
      }
      catch (InvalidArgumentException iae)
      {
         throw new WebApplicationException(iae, createErrorResponse(iae, 400));
      }
      catch (Exception others)
      {
         throw new WebApplicationException(others, createErrorResponse(others, 500));
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   @GET
   @Path("{repositoryId}/allowableactions/{objectId}")
   @Produces("application/atom+xml;type=allowableActions")
   public Response getAllowableActions(@PathParam("repositoryId") String repositoryId,
      @PathParam("objectId") String objectId)
   {
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         AllowableActions result = connection.getAllowableActions(objectId);
         AllowableActionsElement el = AbderaFactory.getInstance().getFactory().newElement(AtomCMIS.ALLOWABLE_ACTIONS);
         el.build(result);
         return Response.ok(el).header(HttpHeaders.CACHE_CONTROL, "no-cache").build();
      }
      catch (ObjectNotFoundException onfe)
      {
         throw new WebApplicationException(onfe, createErrorResponse(onfe, 404));
      }
      catch (InvalidArgumentException iae)
      {
         throw new WebApplicationException(iae, createErrorResponse(iae, 400));
      }
      catch (Exception others)
      {
         throw new WebApplicationException(others, createErrorResponse(others, 500));
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   @GET
   @Path("{repositoryId}/typedescendants")
   @Produces("application/cmistree+xml")
   public Response getAllTypeDescendants(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      // The same jobs as 'getTypeDescendants()' but respect URL pattern.
      return getFeed(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/types")
   @Produces("application/atom+xml;type=entry")
   public Response getAllTypes(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      // The same jobs as 'getTypes()' but respect URL pattern.
      return getTypes(httpRequest, repositoryId);
   }

   @GET
   // @Path("{repositoryId}/versions/{versionSeriesId}")
   @Path("{repositoryId}/versions/{versionSeriesId:.+}")
   @Produces("application/atom+xml;type=entry")
   public Response getAllVersions(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/policies/{objectId}")
   public Response getAppliedPolicies(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/changes")
   public Response getContentChanges(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/checkedout")
   public Response getCheckedOut(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/children/{folderId}")
   @Produces("application/atom+xml;type=feed")
   public Response getChildren(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/file/{documentId}")
   public Response getContentStream(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      RequestContext request = initRequestContext(httpRequest);
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         AbstractCmisCollection<?> collection = getCollection(request, connection);
         ResponseContext abderaResponse = collection.getMedia(request);
         ResponseBuilder builder = Response.status(abderaResponse.getStatus());
         copyAbderaHeaders(builder, abderaResponse);
         builder.entity(abderaResponse);
         // Cache-Control headers ?
         return builder.build();
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }

   }

   @GET
   @Path("{repositoryId}/descendants/{folderId}")
   @Produces("application/cmistree+xml")
   public Response getDescendants(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/foldertree/{folderId}")
   @Produces("application/atom+xml;type=feed")
   public Response getFolderTree(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/object/{objectId}")
   @Produces("application/atom+xml;type=entry")
   public Response getObjectById(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return getEntry(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/objectbypath")
   @Produces("application/atom+xml;type=entry")
   public Response getObjectByPath(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      return getEntry(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/parents/{objectId}")
   public Response getObjectParents(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/relationships/{objectId}")
   @Produces("application/atom+xml;type=entry")
   public Response getRelationships(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/alternate/{documentId}/{streamId}")
   public Response getRendition(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId, @PathParam("documentId") String documentId)
   {
      return getContentStream(httpRequest, repositoryId);
   }

   @GET
   public Response getRepositories(@Context HttpServletRequest httpRequest, @Context UriInfo uriInfo)
   {
      String repositoryId = httpRequest.getParameter("repositoryId");
      if (repositoryId != null && !repositoryId.isEmpty())
      {
         return makeRepositoryInfoResponse(httpRequest, uriInfo.getBaseUri(), repositoryId);
      }
      Service service = AbderaFactory.getInstance().getFactory().newService();
      service.declareNS(AtomCMIS.CMISRA_NS_URI, AtomCMIS.CMISRA_PREFIX);
      Set<RepositoryShortInfo> shortInfos = CmisRegistry.getInstance().getStorageInfos();
      RequestContext request = initRequestContext(httpRequest);
      if (shortInfos != null && !shortInfos.isEmpty())
      {
         for (RepositoryShortInfo info : shortInfos)
         {
            Workspace ws = service.addWorkspace(info.getRepositoryId());
            ws.setTitle(info.getRepositoryName());
            RepositoryInfoTypeElement repoInfoElement = ws.addExtension(AtomCMIS.REPOSITORY_INFO);
            repoInfoElement.addSimpleExtension(AtomCMIS.REPOSITORY_ID, info.getRepositoryId());
            repoInfoElement.addSimpleExtension(AtomCMIS.REPOSITORY_NAME, info.getRepositoryName());
            repoInfoElement.addSimpleExtension(AtomCMIS.ROOT_FOLDER_ID, info.getRootFolderId());

            String repoPath =
               UriBuilder.fromUri(uriInfo.getBaseUri()).path(getClass()).path(info.getRepositoryId()).build()
                  .toString();

            Collection<CollectionInfo> collectionsInfo = getCollectionsInfo(request);
            for (CollectionInfo collectionInfo : collectionsInfo)
            {
               CmisCollectionInfo cmisCollectionInfo = (CmisCollectionInfo)collectionInfo;
               String collectionType = cmisCollectionInfo.getCollectionType();
               if (AtomCMIS.COLLECTION_TYPE_ROOT.equals(collectionType))
                  ws.addCollection(((CmisCollectionInfo)collectionInfo).asCollectionElement(request, repoPath, info
                     .getRootFolderId()));
               else
                  ws.addCollection(((CmisCollectionInfo)collectionInfo).asCollectionElement(request, repoPath));
            }

            includeURITemplates(ws, repoPath);
         }
      }
      return Response.ok().entity(service).header(HttpHeaders.CACHE_CONTROL, "no-cache").type(
         MediaType.APPLICATION_ATOM_XML).build();
   }

   @GET
   @Path("{repositoryId}")
   public Response getRepositoryInfo(@Context HttpServletRequest httpRequest, @Context UriInfo uriInfo,
      @PathParam("repositoryId") String repositoryId)
   {
      String queryRepositoryId = httpRequest.getParameter("repositoryId");
      if (queryRepositoryId != null && !queryRepositoryId.isEmpty())
         return makeRepositoryInfoResponse(httpRequest, uriInfo.getBaseUri(), queryRepositoryId);
      return makeRepositoryInfoResponse(httpRequest, uriInfo.getBaseUri(), repositoryId);
   }

   private Response makeRepositoryInfoResponse(HttpServletRequest httpRequest, URI baseUri, String repositoryId)
   {
      Service service = AbderaFactory.getInstance().getFactory().newService();
      service.declareNS(AtomCMIS.CMIS_NS_URI, AtomCMIS.CMIS_PREFIX);
      service.declareNS(AtomCMIS.CMISRA_NS_URI, AtomCMIS.CMISRA_PREFIX);
      addCmisRepository(httpRequest, service, repositoryId, baseUri);
      Document<Service> serviceDocument = service.getDocument();
      serviceDocument.setCharset("utf-8");
      ResponseContext abderaResponse = new BaseResponseContext<Document<Service>>(serviceDocument);
      abderaResponse.setStatus(200);
      return Response.ok(abderaResponse).header(HttpHeaders.CACHE_CONTROL, "no-cache")
         .type(MediaType.APPLICATION_ATOM_XML).build();
   }

   @GET
   @Path("{repositoryId}/typebyid/{typeId}")
   @Produces("application/atom+xml;type=entry")
   public Response getTypeById(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return getEntry(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/typedescendants/{typeId}")
   @Produces("application/cmistree+xml")
   public Response getTypeDescendants(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/types/{typeId}")
   @Produces("application/atom+xml;type=entry")
   public Response getTypes(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @POST
   @Path("{repositoryId}/query")
   public Response query(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      RequestContext request = initRequestContext(httpRequest);
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         AbstractCmisCollection<?> collection = getCollection(request, connection);
         ResponseContext abderaResponse = collection.getFeed(request);
         ResponseBuilder builder =
            Response.status(abderaResponse.getStatus() == 200 ? 201 : abderaResponse.getStatus());
         copyAbderaHeaders(builder, abderaResponse);
         builder.header(HttpHeaders.CACHE_CONTROL, "no-cache");
         return builder.entity(abderaResponse).build();
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   @GET
   @Path("{repositoryId}/query")
   public Response queryGET(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   @DELETE
   @Path("{repositoryId}/policies/{objectId}")
   public Response removePolicy(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return deleteItem(repositoryId, httpRequest);
   }

   @PUT
   @Path("{repositoryId}/file/{objectId}")
   public Response setContentStream(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      RequestContext request = initRequestContext(httpRequest);
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         AbstractCmisCollection<?> collection = getCollection(request, connection);
         ResponseContext abderaResponse = collection.putMedia(request);
         ResponseBuilder builder = Response.status(abderaResponse.getStatus());
         copyAbderaHeaders(builder, abderaResponse);
         return builder.entity(abderaResponse).build();
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   @POST
   @Path("{repositoryId}/file/{objectId}")
   @Consumes("multipart/form-data")
   public Response setContentStream(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId, @QueryParam(HttpHeaders.CONTENT_TYPE) String contentType,
      Iterator<FileItem> files)
   {
      if (files.hasNext())
      {
         FileItem file = files.next();
         // In fact expected to get just one file. Other items
         // in iterator may be simple form fields, and we are not
         // care about it. Just skip all of them.
         if (!file.isFormField())
         {
            try
            {
               // Content-Type passed as query parameter, we do not
               // wont to use passed by browser. But if parameter does
               // not exists then try to get media type passed by browser.
               if (contentType == null)
               {
                  contentType = file.getContentType();
               }

               RequestContext request = initRequestContext(httpRequest);
               Connection connection = null;
               try
               {
                  connection = CmisRegistry.getInstance().getConnection(repositoryId);
                  AbstractCmisCollection<?> collection = getCollection(request, connection);
                  collection.putMedia(null, new javax.activation.MimeType(contentType), null, file.getInputStream(),
                     request);
                  return Response.status(201).build();
               }
               finally
               {
                  if (connection != null)
                  {
                     connection.close();
                  }
               }
            }
            catch (IOException ioe)
            {
               throw new WebApplicationException(ioe, createErrorResponse(ioe, 500));
            }
            catch (ResponseContextException rce)
            {
               throw new WebApplicationException(rce, createErrorResponse(rce, rce.getResponseContext().getStatus()));
            }
            catch (MimeTypeParseException mte)
            {
               throw new WebApplicationException(mte, createErrorResponse(mte, 400));
            }
         }
      }
      throw new WebApplicationException(new InvalidArgumentException("Content of document is missing."), 400);
   }

   @PUT
   @Path("{repositoryId}/object/{objectId}")
   @Produces("application/atom+xml;type=entry")
   public Response updateProperties(@Context HttpServletRequest httpRequest,
      @PathParam("repositoryId") String repositoryId)
   {
      RequestContext request = initRequestContext(httpRequest);
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         AbstractCmisCollection<?> collection = getCollection(request, connection);
         ResponseContext abderaResponse = collection.putEntry(request);
         ResponseBuilder builder = Response.status(abderaResponse.getStatus());
         copyAbderaHeaders(builder, abderaResponse);
         return builder.entity(abderaResponse).build();
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   private void copyAbderaHeaders(ResponseBuilder responseBuilder, ResponseContext abderaResponse)
   {
      for (String headerName : abderaResponse.getHeaderNames())
      {
         for (Object v : abderaResponse.getHeaders(headerName))
         {
            // For now just be sure not get errors if RESTful framework.
            responseBuilder.header(headerName, v.toString());
         }
      }
   }

   @POST
   @Path("{repositoryId}/unfiled")
   public Response unfile(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
      throws Exception
   {
      return createItem(repositoryId, httpRequest);
   }

   @GET
   @Path("{repositoryId}/unfiled")
   public Response getUnfiled(@Context HttpServletRequest httpRequest, @PathParam("repositoryId") String repositoryId)
   {
      return getFeed(repositoryId, httpRequest);
   }

   private Workspace addCmisRepository(HttpServletRequest httpRequest, Service service, String repositoryId, URI baseUri)
   {
      RepositoryInfo repoInfo;
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         repoInfo = connection.getStorage().getRepositoryInfo();
      }
      catch (InvalidArgumentException iae)
      {
         throw new WebApplicationException(iae, createErrorResponse(iae, 400));
      }
      catch (Exception others)
      {
         throw new WebApplicationException(others, createErrorResponse(others, 500));
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }

      Workspace ws = service.addWorkspace(repositoryId);
      ws.setTitle(repoInfo.getRepositoryName());
      RepositoryInfoTypeElement repoInfoElement = ws.addExtension(AtomCMIS.REPOSITORY_INFO);
      repoInfoElement.build(repoInfo);

      String repoPath = UriBuilder.fromUri(baseUri).path(getClass()).path(repositoryId).build().toString();

      RequestContext request = initRequestContext(httpRequest);

      Collection<CollectionInfo> collectionsInfo = getCollectionsInfo(request);
      for (CollectionInfo collectionInfo : collectionsInfo)
      {
         CmisCollectionInfo cmisCollectionInfo = (CmisCollectionInfo)collectionInfo;
         String collectionType = cmisCollectionInfo.getCollectionType();
         if (AtomCMIS.COLLECTION_TYPE_ROOT.equals(collectionType))
            ws.addCollection(((CmisCollectionInfo)collectionInfo).asCollectionElement(request, repoPath, repoInfo
               .getRootFolderId()));
         else
            ws.addCollection(((CmisCollectionInfo)collectionInfo).asCollectionElement(request, repoPath));
      }

      includeURITemplates(ws, repoPath);

      // Add links
      Factory factory = AbderaFactory.getInstance().getFactory();
      Link typeTypeDescendantsLink = factory.newLink(ws);
      typeTypeDescendantsLink.setTitle("Type descendants");
      typeTypeDescendantsLink.setRel(AtomCMIS.LINK_CMIS_TYPEDESCENDANTS);
      typeTypeDescendantsLink.setMimeType(AtomCMIS.MEDIATYPE_CMISTREE);
      typeTypeDescendantsLink.setHref(repoPath + "/typedescendants");

      RepositoryCapabilities capabilities = repoInfo.getCapabilities();
      if (capabilities.isCapabilityGetDescendants())
      {
         Link descendantsLink = factory.newLink(ws);
         descendantsLink.setTitle("Root folder descendants");
         descendantsLink.setRel(AtomCMIS.LINK_CMIS_ROOTDESCENDANTS);
         descendantsLink.setMimeType(AtomCMIS.MEDIATYPE_CMISTREE);
         descendantsLink.setHref(repoPath + "/descendants/" + repoInfo.getRootFolderId());
      }
      if (capabilities.isCapabilityGetFolderTree())
      {
         Link treeLink = factory.newLink(ws);
         treeLink.setTitle("Root folder tree");
         treeLink.setRel(AtomCMIS.LINK_CMIS_FOLDERTREE);
         treeLink.setMimeType(AtomCMIS.MEDIATYPE_CMISTREE);
         treeLink.setHref(repoPath + "/foldertree/" + repoInfo.getRootFolderId());
      }
      if (CapabilityChanges.NONE != capabilities.getCapabilityChanges())
      {
         Link treeLink = factory.newLink(ws);
         treeLink.setTitle("Changes log");
         treeLink.setRel(AtomCMIS.LINK_CMIS_CHANGES);
         treeLink.setMimeType(AtomCMIS.MEDIATYPE_ATOM_FEED);
         treeLink.setHref(repoPath + "/changes");
      }

      return ws;
   }

   private void includeURITemplates(Workspace ws, String repoPath)
   {
      // objectbyid template
      CmisUriTemplateType objectById = new CmisUriTemplateType();
      objectById.setMediatype(AtomCMIS.MEDIATYPE_ATOM_ENTRY);
      objectById.setTemplate(new StringBuilder() //
         .append(repoPath) //
         .append("/object/{id}?") //
         .append("filter={filter}&") //
         .append("includeAllowableActions={includeAllowableActions}&") //
         .append("includePolicyIds={includePolicyIds}&") //
         .append("includeRelationships={includeRelationships}&") //
         .append("includeACL={includeACL}&") //
         .append("renditionFilter={renditionFilter}").toString());
      objectById.setType(AtomCMIS.URITEMPLATE_OBJECTBYID);
      UriTemplateTypeElement objectByIdElement = ws.addExtension(AtomCMIS.URITEMPLATE);
      objectByIdElement.build(objectById);

      // objectbypath template
      CmisUriTemplateType objectByPath = new CmisUriTemplateType();
      objectByPath.setMediatype(AtomCMIS.MEDIATYPE_ATOM_ENTRY);
      objectByPath.setTemplate(new StringBuilder() //
         .append(repoPath) //
         .append("/objectbypath?")//
         .append("path={path}&")//
         .append("filter={filter}&")//
         .append("includeAllowableActions={includeAllowableActions}&")//
         .append("includePolicyIds={includePolicyIds}&")//
         .append("includeRelationships={includeRelationships}&")//
         .append("includeACL={includeACL}&") //
         .append("renditionFilter={renditionFilter}").toString());
      objectByPath.setType(AtomCMIS.URITEMPLATE_OBJECTBYPATH);
      UriTemplateTypeElement folderByPathElement = ws.addExtension(AtomCMIS.URITEMPLATE);
      folderByPathElement.build(objectByPath);

      // query template
      CmisUriTemplateType query = new CmisUriTemplateType();
      query.setMediatype(AtomCMIS.MEDIATYPE_ATOM_FEED);
      query.setTemplate(new StringBuilder() //
         .append(repoPath) //
         .append("/query?")//
         .append("q={q}&")//
         .append("searchAllVersions={searchAllVersions}&") //
         .append("maxItems={maxItems}&") //
         .append("skipCount={skipCount}&") //
         .append("includeAllowableActions={includeAllowableActions}&")//
         .append("includeRelationships={includeRelationships}").toString());
      query.setType(AtomCMIS.URITEMPLATE_QUERY);
      UriTemplateTypeElement queryElement = ws.addExtension(AtomCMIS.URITEMPLATE);
      queryElement.build(query);

      // typebyid template
      CmisUriTemplateType typeById = new CmisUriTemplateType();
      typeById.setMediatype(AtomCMIS.MEDIATYPE_ATOM_ENTRY);
      typeById.setTemplate(repoPath + "/typebyid/{id}");
      typeById.setType(AtomCMIS.URITEMPLATE_TYPEBYID);
      UriTemplateTypeElement typeByIdElement = ws.addExtension(AtomCMIS.URITEMPLATE);
      typeByIdElement.build(typeById);

      // changes
      CmisUriTemplateType changes = new CmisUriTemplateType();
      changes.setMediatype(AtomCMIS.MEDIATYPE_ATOM_FEED);
      changes.setTemplate(new StringBuilder() //
         .append(repoPath) //
         .append("/changes?")//
         .append("changeLogToken={changeLogToken}&")//
         .append("includeProperties={includeProperties}&") //
         .append("includePolicyIds={includePolicyIds}&") //
         .append("includeACL={includeACL}&") //
         .append("filter={filter}&")//
         .append("maxItems={maxItems}").toString());
      changes.setType(AtomCMIS.URITEMPLATE_CHANGES);
      UriTemplateTypeElement changesElement = ws.addExtension(AtomCMIS.URITEMPLATE);
      changesElement.build(changes);
   }

   protected Response createErrorResponse(Throwable t, int status)
   {
      return Response.status(status).entity(t.getMessage()).type("text/plain").build();
   }

   protected Response createItem(String repositoryId, HttpServletRequest httpRequest)
   {
      RequestContext request = initRequestContext(httpRequest);
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         AbstractCmisCollection<?> collection = getCollection(request, connection);
         ResponseContext abderaResponse = collection.postEntry(request);
         ResponseBuilder builder = Response.status(abderaResponse.getStatus());
         copyAbderaHeaders(builder, abderaResponse);
         return builder.entity(abderaResponse).build();
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   protected Response deleteItem(String repositoryId, HttpServletRequest httpRequest)
   {
      RequestContext request = initRequestContext(httpRequest);
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         AbstractCmisCollection<?> collection = getCollection(request, connection);
         ResponseContext abderaResponse = collection.deleteEntry(request);
         ResponseBuilder builder = Response.status(abderaResponse.getStatus());
         copyAbderaHeaders(builder, abderaResponse);
         return builder.entity(abderaResponse).build();
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   protected AbstractCmisCollection<?> getCollection(RequestContext request, Connection connection)
   {
      WorkspaceManagerImpl wm = (WorkspaceManagerImpl)provider.getWorkspaceManager(request);
      AbstractCmisCollection<?> collection = wm.getCollectionAdapter(request, connection);
      return collection;
   }

   protected Collection<CollectionInfo> getCollectionsInfo(RequestContext request)
   {
      Collection<WorkspaceInfo> workspaces = (provider).getWorkspaceManager(request).getWorkspaces(request);
      Collection<CollectionInfo> collections = workspaces.iterator().next().getCollections(request);
      return collections;
   }

   protected Response getEntry(String repositoryId, HttpServletRequest httpRequest)
   {
      RequestContext request = initRequestContext(httpRequest);
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         AbstractCmisCollection<?> collection = getCollection(request, connection);
         ResponseContext abderaResponse = collection.getEntry(request);
         ResponseBuilder builder = Response.status(abderaResponse.getStatus());
         copyAbderaHeaders(builder, abderaResponse);
         builder.header(HttpHeaders.CACHE_CONTROL, "no-cache");
         return builder.entity(abderaResponse).build();
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   protected Response getFeed(String repositoryId, HttpServletRequest httpRequest)
   {
      RequestContext request = initRequestContext(httpRequest);
      Connection connection = null;
      try
      {
         connection = CmisRegistry.getInstance().getConnection(repositoryId);
         AbstractCmisCollection<?> collection = getCollection(request, connection);
         ResponseContext abderaResponse = collection.getFeed(request);
         ResponseBuilder builder = Response.status(abderaResponse.getStatus());
         copyAbderaHeaders(builder, abderaResponse);
         builder.header(HttpHeaders.CACHE_CONTROL, "no-cache");
         return builder.entity(abderaResponse).build();
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   private RequestContext initRequestContext(HttpServletRequest httpRequest)
   {
      return new ServletRequestContext(provider, httpRequest)
      {
         @Override
         public String getTargetPath()
         {
            String uri = getUri().toString();
            String bpath = getTargetBasePath();
            return uri.substring(bpath.length());
         }
      };
   }

}
