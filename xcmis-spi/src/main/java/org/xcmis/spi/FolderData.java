/*
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

package org.xcmis.spi;

/**
 * @author <a href="mailto:andrey00x@gmail.com">Andrey Parfonov</a>
 * @version $Id: $
 */
public interface FolderData extends ObjectData
{

   /**
    * Get absolute path to folder. Path to root folder is '/'. This method is
    * shortcut to property 'cmis:path'.
    * 
    * @return path to this folder
    */
   String getPath();

   /**
    * Get children of current folder.
    * 
    * Implementation Compatibility: SHOULD be implemented if the implementation 
    * supports hierarchical view (capabilityGetDescendants, capabilityGetFolderTree). 
    * Otherwise empty ItemsIterator should be returned.
    * 
    * @param orderBy comma-separated list of query names and the ascending
    *        modifier 'ASC' or the descending modifier 'DESC' for each query
    *        name. This parameter may be ignored if implementation has not
    *        possibility to sort items
    * @return children iterator. If folder does not contains any children then
    *         empty {@link ItemsIterator} must be returned, never
    *         <code>null</code>.
    */
   ItemsIterator<ObjectData> getChildren(String orderBy);

   /**
    * @return <code>true</code> if current folder has children and
    *         <code>otherwise</code>
    */
   boolean hasChildren();

   /**
    * Add existed fileable object in this folder. 
    * 2.2.5.1 addObjectToFolder
    * 
    * Implementation Compatibility: SHOULD be implemented if the implementation 
    * supports multifiling capability (capabilityMultifiling). 
    * Otherwise {@link NotSupportedException} should be thrown.
    * 
    * @param object the object to be added
    * @throws ConstraintException if <code>object</code> has type that is
    *         unsupported by current folder. See
    *         {@link CMIS#ALLOWED_CHILD_OBJECT_TYPE_IDS}
    * @throws NotSupportedException if multifiling capability is not supported
    */
   void addObject(ObjectData object) throws ConstraintException;

   /**
    * Remove fileable object from current folder. This method don't remove
    * object just unsigned it as child of this folder. 
    * 
    * Implementation Compatibility: SHOULD be implemented if the implementation 
    * supports unfiling capability (capabilityUnfiling). 
    * Otherwise {@link NotSupportedException} should be thrown.
    * 
    * @param object object to be removed from current folder
    * @throws NotSupportedException if unfiling capability is not supported
    */
   void removeObject(ObjectData object);

   /**
    * Check is specified type in list of allowed child object types. Info about
    * allowed child object types may be provided by property
    * {@value CMIS#ALLOWED_CHILD_OBJECT_TYPE_IDS}. Empty or <code>null</code>
    * property minds there is no any constrains about child type for this folder
    * and any fileable objects may be created or added (if multifiling
    * supported) in this folder.
    * 
    * @param typeId type to be checked
    * @return <code>true</code> if type allowed as child and <code>false</code>
    *         otherwise
    */
   boolean isAllowedChildType(String typeId);

   /**
    * @return <code>true</code> if current folder is root folder and
    *         <code>otherwise</code>
    */
   boolean isRoot();

}
