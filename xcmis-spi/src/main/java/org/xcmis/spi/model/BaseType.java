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

package org.xcmis.spi.model;

/**
 * Base CMIS types.
 * 
 * @author <a href="mailto:andrey00x@gmail.com">Andrey Parfonov</a>
 * @version $Id: $
 */
public enum BaseType {

   /** A Document is information entity. */
   DOCUMENT("cmis:document"),

   /**
    * A Folder serves as the anchor for a collection of file-able objects.
    */
   FOLDER("cmis:folder"),
   /**
    * A Relationship instantiates relationship between a source object and a
    * target object.
    */
   RELATIONSHIP("cmis:relationship"), //
   /**
    * A Policy object represents an administrative policy that can be applied to
    * other objects.
    */
   POLICY("cmis:policy");

   private final String value;

   private BaseType(String value)
   {
      this.value = value;
   }

   public String value()
   {
      return value;
   }

   public static BaseType fromValue(String value)
   {
      for (BaseType e : BaseType.values())
      {
         if (e.value.equals(value))
            return e;
      }
      throw new IllegalArgumentException(value);
   }

   @Override
   public String toString()
   {
      return value;
   }
}
