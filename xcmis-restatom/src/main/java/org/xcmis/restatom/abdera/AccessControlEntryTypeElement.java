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

package org.xcmis.restatom.abdera;

import org.apache.abdera.factory.Factory;
import org.apache.abdera.model.Element;
import org.apache.abdera.model.ExtensibleElementWrapper;
import org.apache.abdera.parser.stax.FOMExtensibleElement;
import org.xcmis.restatom.AtomCMIS;
import org.xcmis.spi.model.AccessControlEntry;

import java.util.List;

import javax.xml.namespace.QName;

/**
 * @author <a href="mailto:andrey.parfonov@exoplatform.com">Andrey Parfonov</a>
 * @version $Id: AccessControlEntryTypeElement.java 2 2010-02-04 17:21:49Z
 *          andrew00x $
 */
public class AccessControlEntryTypeElement extends ExtensibleElementWrapper
{

   /**
    * Instantiates a new access control entry type element.
    * 
    * @param internal the internal
    */
   public AccessControlEntryTypeElement(Element internal)
   {
      super(internal);
   }

   /**
    * Instantiates a new access control entry type element.
    * 
    * @param factory the factory
    * @param qname the qname
    */
   public AccessControlEntryTypeElement(Factory factory, QName qname)
   {
      super(factory, qname);
   }

   /**
    * Builds the element.
    * 
    * @param accessControlEntry the AccessControlEntry
    */
   public void build(AccessControlEntry accessControlEntry)
   {
      declareNS(org.apache.abdera.util.Constants.ATOM_NS, "atom");
      declareNS(org.apache.abdera.util.Constants.APP_NS, "app");

      // PRINCIPAL
      if (accessControlEntry.getPrincipal() != null)
      {
         String principal = accessControlEntry.getPrincipal();
         FOMExtensibleElement principalElement = addExtension(AtomCMIS.PRINCIPAL);
         principalElement.addSimpleExtension(AtomCMIS.PRINCIPAL_ID, principal);
      }

      // PERMISSION
      if (accessControlEntry.getPermissions() != null && accessControlEntry.getPermissions().size() > 0)
      {
         for (String one : accessControlEntry.getPermissions())
         {
            addSimpleExtension(AtomCMIS.PERMISSION, one);
         }
      }

      Boolean direct = accessControlEntry.isDirect();
      addSimpleExtension(AtomCMIS.DIRECT, ((direct != null && direct) ? "true" : "false"));
   }

   /**
    * Gets the AccessControlEntry.
    * 
    * @return AccessControlEntry
    */
   public AccessControlEntry getACE()
   {

      AccessControlEntry accessControlEntry = new AccessControlEntry();

      // PRINCIPAL
      FOMExtensibleElement principalElement = getExtension(AtomCMIS.PRINCIPAL);
      if (principalElement != null)
      {
         String principalId = principalElement.getSimpleExtension(AtomCMIS.PRINCIPAL_ID);
         if (principalId != null)
         {
            accessControlEntry.setPrincipal(principalId);
         }
      }

      // PERMISSION
      List<ExtensibleElementWrapper> permissionElements = getExtensions(AtomCMIS.PERMISSION);
      if (permissionElements != null && !permissionElements.isEmpty())
      {
         for (ExtensibleElementWrapper permissionElement : permissionElements)
         {
            if (permissionElement.getText() != null)
            {
               accessControlEntry.getPermissions().add(permissionElement.getText());
            }
         }
      }

      // DIRECT
      // TODO direct element
      //            Element direct = el.getExtension(AtomCMIS.DIRECT);
      //            accessControlEntry.setDirect(direct == null ? true : Boolean.parseBoolean(direct.getText()));

      return accessControlEntry;
   }

}
