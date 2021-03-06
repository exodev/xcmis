/*
 * Copyright (C) 2009 eXo Platform SAS.
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

/**
 * Created by The eXo Platform SAS. <br/>Date:
 * 
 * @author <a href="karpenko.sergiy@gmail.com">Karpenko Sergiy</a>
 * @version $Id: TransactionLogException.java 2 2010-02-04 17:21:49Z andrew00x $
 */
public class TransactionLogException extends IndexTransactionException
{

   /**
    * Constructor.
    * 
    * @param message - exception description
    */
   public TransactionLogException(String message)
   {
      super(message);
   }

   /**
    * Constructor.
    * 
    * @param message - exception description
    * @param cause - exception cause.
    */
   public TransactionLogException(String message, Throwable cause)
   {
      super(message, cause);
   }

}
