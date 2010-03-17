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

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.xcmis.search.SearchServiceException;

/**
 * Created by The eXo Platform SAS.
 * 
 * @author <a href="mailto:Sergey.Kabashnyuk@gmail.com">Sergey Kabashnyuk</a>
 * @version $Id: IndexTransactionException.java 2 2010-02-04 17:21:49Z andrew00x $
 */
public class IndexTransactionException extends SearchServiceException
{
   /**
    * 
    */
   private static final long serialVersionUID = -1695255073054780683L;

   /**
    * Class logger.
    */
   private static final Log LOG = ExoLogger.getLogger(IndexTransactionException.class);

   /**
    * 
    */
   public IndexTransactionException()
   {
   }

   /**
    * @param message
    */
   public IndexTransactionException(String message)
   {
      super(message);
   }

   /**
    * @param cause
    */
   public IndexTransactionException(Throwable cause)
   {
      super(cause);
   }

   /**
    * @param message
    * @param cause
    */
   public IndexTransactionException(String message, Throwable cause)
   {
      super(message, cause);
   }
}