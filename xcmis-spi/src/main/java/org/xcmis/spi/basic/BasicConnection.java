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
package org.xcmis.spi.basic;

import org.xcmis.spi.Connection;
import org.xcmis.spi.ObjectData;
import org.xcmis.spi.Storage;
import org.xcmis.spi.UpdateConflictException;

/**
 * Empty Connection impl
 */
public class BasicConnection extends Connection
{

   public BasicConnection(Storage storage)
   {
      super(storage);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void checkConnection() throws IllegalStateException
   {
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void close()
   {
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void validateChangeToken(ObjectData object, String changeToken) throws UpdateConflictException
   {
   }

}
