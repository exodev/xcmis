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

package org.xcmis.spi;

import org.xcmis.spi.model.Rendition;
import org.xcmis.spi.utils.MimeType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:andrey.parfonov@exoplatform.com">Andrey Parfonov</a>
 * @version $Id: RenditionFilter.java 265 2010-03-04 11:10:52Z andrew00x $
 */
public class RenditionFilter
{

   /** None filter to include all renditions. */
   public static final String ANY = CmisConstants.WILDCARD;

   /** None filter to exclude all renditions. */
   public static final String NONE = "cmis:none";

   /** None filter to accept only renditions with 'cmis:thumbnail' kind. */
   public static final String THUMBNAIL = "cmis:thumbnail";

   /** Filter instance with 'cmis:thumbnail' kind. */
   public static final RenditionFilter THUMBNAIL_FILTER;

   /** Filter instance with all renditions included. */
   public static final RenditionFilter ANY_FILTER;

   /** Filter instance with none renditions included. */
   public static final RenditionFilter NONE_FILTER;

   static
   {
      THUMBNAIL_FILTER = new RenditionFilter();
      Set<String> tmp = new HashSet<String>();
      tmp.add(THUMBNAIL);
      THUMBNAIL_FILTER.kinds = Collections.unmodifiableSet(tmp);
      THUMBNAIL_FILTER.mediaTypes = Collections.emptySet();
      ANY_FILTER = new RenditionFilter();
      ANY_FILTER.anyRenditions = true;
      NONE_FILTER = new RenditionFilter();
      NONE_FILTER.kinds = Collections.emptySet();
      NONE_FILTER.mediaTypes = Collections.emptySet();
   }

   /** Pattern for parsing source filter string. */
   private static final Pattern SPLITTER = Pattern.compile("\\s*,\\s*");

   /** Whether any renditions. */
   private boolean anyRenditions = false;

   /** Kinds. */
   private Set<String> kinds;

   /** Media types. */
   private Set<MimeType> mediaTypes;

   /**
    * Construct new Rendition Filter.
    * 
    * @param filterString string that contains either '*' or comma-separated
    *        list of rendition's kind or mime-types. An arbitrary number of
    *        space allowed before and after each comma. Each token will be
    *        interpreted as rendition mime-type if it has form 'type/sub-type'
    *        otherwise it will be interpreted as kind.
    * @throws FilterNotValidException if <code>filterString</code> is invalid
    */
   public RenditionFilter(String filterString) throws FilterNotValidException
   {
      if (filterString == null || filterString.length() == 0 || NONE.equals(filterString.trim()))
      {
         // Filter will exclude all associated renditions.
         this.kinds = Collections.emptySet();
         this.mediaTypes = Collections.emptySet();
      }
      else if (ANY.equals(filterString.trim()))
      {
         this.anyRenditions = true;
      }
      else
      {
         filterString = filterString.trim();
         this.kinds = new HashSet<String>();
         this.mediaTypes = new HashSet<MimeType>();
         for (String token : SPLITTER.split(filterString))
         {
            if (token.length() > 0 && !token.equals(ANY))
            {
               for (char ch : token.toCharArray())
               {
                  if (Character.isWhitespace(ch))
                  {
                     String msg = "Invalid filter \"" + filterString + "\". Filter's tokens must not have whitespace.";
                     throw new FilterNotValidException(msg);
                  }
               }
               if (token.indexOf('/') > 0)
                  this.mediaTypes.add(MimeType.fromString(token));
               else
                  this.kinds.add(token);
            }
            else
            {
               // String contains empty token or some tokens and special token '*'
               String msg =
                  "Invalid filter \"" + filterString
                     + "\". Filter must contains either '*' OR comma-separated list mime-types or kinds of renditions.";
               throw new FilterNotValidException(msg);
            }
         }
      }
   }

   protected RenditionFilter()
   {
   }

   /**
    * {@inheritDoc}
    */
   public boolean accept(Rendition rendition)
   {
      if (anyRenditions)
         return true;
      if (kinds.contains(rendition.getKind()))
         return true;
      MimeType toCheck = MimeType.fromString(rendition.getMimeType());
      for (MimeType mediaType : mediaTypes)
      {
         if (mediaType.match(toCheck))
            return true;
      }
      return false;
   }

   // TODO : do smarter with filter. Temporary just avoid to do unnecessary work and skip results. 
   public boolean isNone()
   {
      return !anyRenditions && mediaTypes.size() == 0 && kinds.size() == 0;
   }

}
