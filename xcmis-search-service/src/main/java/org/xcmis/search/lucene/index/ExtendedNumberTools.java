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
package org.xcmis.search.lucene.index;

import org.apache.lucene.document.NumberTools;

import java.math.BigDecimal;

/**
 * Helper class. To convert double and BigDecimal to sorted strings
 */
public class ExtendedNumberTools
{

   /**
    * Converts a long to a String suitable for indexing.
    * 
    * @param doubleVal double value
    * @return double as string
    */
   public static String doubleToString(double doubleVal)
   {
      long f = Double.doubleToRawLongBits(doubleVal);
      if (f < 0)
      {
         f ^= 0x7fffffffffffffffL;
      }
      return NumberTools.longToString(f);
   }

   /**
    * Converts a long to a String suitable for indexing.
    * 
    * @param bigDecimal BigDecimal value
    * @return double as string
    */
   public static String bigDecimalToString(BigDecimal bigDecimal)
   {
      return doubleToString(bigDecimal.doubleValue());
   }
}
