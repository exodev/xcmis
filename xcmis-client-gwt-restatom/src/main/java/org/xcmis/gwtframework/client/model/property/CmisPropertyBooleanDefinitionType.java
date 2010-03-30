/**
 *  Copyright (C) 2010 eXo Platform SAS.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xcmis.gwtframework.client.model.property;

import org.xcmis.gwtframework.client.model.choice.CmisChoiceBoolean;

import java.util.ArrayList;
import java.util.List;

/**
 * @author 
 * @version $Id: 
 *
 */
public class CmisPropertyBooleanDefinitionType extends CmisPropertyDefinitionType
{

   /**
    * Default value.
    */
   protected CmisPropertyBoolean defaultValue;

   /**
    * Choice.
    */
   protected List<CmisChoiceBoolean> choice;

   /**
    * @return {@link CmisPropertyBoolean}
    */
   public CmisPropertyBoolean getDefaultValue()
   {
      return defaultValue;
   }

   /**
    * @param value value
    */
   public void setDefaultValue(CmisPropertyBoolean value)
   {
      this.defaultValue = value;
   }

   /**
    * @return List containing {@link CmisChoiceBoolean}
    */
   public List<CmisChoiceBoolean> getChoice()
   {
      if (choice == null)
      {
         choice = new ArrayList<CmisChoiceBoolean>();
      }
      return this.choice;
   }

}