/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdfs.server.common;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.BlockReader;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.security.BlockAccessToken;
import org.apache.hadoop.hdfs.security.token.delegation.DelegationTokenIdentifier;
import org.apache.hadoop.hdfs.server.datanode.DatanodeJspHelper;
import org.apache.hadoop.hdfs.server.namenode.DatanodeDescriptor;
import org.apache.hadoop.http.HtmlQuoting;
import org.apache.hadoop.io.WritableUtils;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.UserGroupInformation.AuthenticationMethod;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.util.VersionInfo;

@InterfaceAudience.Private
public class JspHelper {
  final static public String WEB_UGI_PROPERTY_NAME = "dfs.web.ugi";
  public static final String DELEGATION_PARAMETER_NAME = "delegation";
  public static final String SET_DELEGATION = "&" + DELEGATION_PARAMETER_NAME +
                                              "=";
  private static final Log LOG = LogFactory.getLog(JspHelper.class);

  static final Random rand = new Random();

  /** Private constructor for preventing creating JspHelper object. */
  private JspHelper() {} 

  public static DatanodeInfo bestNode(LocatedBlock blk) throws IOException {
    TreeSet<DatanodeInfo> deadNodes = new TreeSet<DatanodeInfo>();
    DatanodeInfo chosenNode = null;
    int failures = 0;
    Socket s = null;
    DatanodeInfo [] nodes = blk.getLocations();
    if (nodes == null || nodes.length == 0) {
      throw new IOException("No nodes contain this block");
    }
    while (s == null) {
      if (chosenNode == null) {
        do {
          chosenNode = nodes[rand.nextInt(nodes.length)];
        } while (deadNodes.contains(chosenNode));
      }
      int index = rand.nextInt(nodes.length);
      chosenNode = nodes[index];

      //just ping to check whether the node is alive
      InetSocketAddress targetAddr = NetUtils.createSocketAddr(
          chosenNode.getHost() + ":" + chosenNode.getInfoPort());
        
      try {
        s = new Socket();
        s.connect(targetAddr, HdfsConstants.READ_TIMEOUT);
        s.setSoTimeout(HdfsConstants.READ_TIMEOUT);
      } catch (IOException e) {
        deadNodes.add(chosenNode);
        s.close();
        s = null;
        failures++;
      }
      if (failures == nodes.length)
        throw new IOException("Could not reach the block containing the data. Please try again");
        
    }
    s.close();
    return chosenNode;
  }

  public static void streamBlockInAscii(InetSocketAddress addr, long blockId, 
                                 BlockAccessToken accessToken, long genStamp, 
                                 long blockSize, 
                                 long offsetIntoBlock, long chunkSizeToView, 
                                 JspWriter out,
                                 Configuration conf) 
    throws IOException {
    if (chunkSizeToView == 0) return;
    Socket s = new Socket();
    s.connect(addr, HdfsConstants.READ_TIMEOUT);
    s.setSoTimeout(HdfsConstants.READ_TIMEOUT);
      
      long amtToRead = Math.min(chunkSizeToView, blockSize - offsetIntoBlock);     
      
      // Use the block name for file name. 
      BlockReader blockReader = 
        BlockReader.newBlockReader(s, addr.toString() + ":" + blockId,
                                             blockId, accessToken, genStamp ,offsetIntoBlock, 
                                             amtToRead, 
                                             conf.getInt("io.file.buffer.size",
                                                         4096));
        
    byte[] buf = new byte[(int)amtToRead];
    int readOffset = 0;
    int retries = 2;
    while ( amtToRead > 0 ) {
      int numRead;
      try {
        numRead = blockReader.readAll(buf, readOffset, (int)amtToRead);
      }
      catch (IOException e) {
        retries--;
        if (retries == 0)
          throw new IOException("Could not read data from datanode");
        continue;
      }
      amtToRead -= numRead;
      readOffset += numRead;
    }
    blockReader = null;
    s.close();
    out.print(HtmlQuoting.quoteHtmlChars(new String(buf)));
  }

  public static void streamBlockInAscii(InetSocketAddress addr, long blockId, 
                                 BlockAccessToken accessToken, long genStamp, 
                                 long blockSize, 
                                 long offsetIntoBlock, long chunkSizeToView, 
                                 JspWriter out,
                                 Configuration conf,
                                 String namenodeHost,
                                 int namenodeInfoPort,
                                 String encodedFilename)
    throws IOException {
    if (chunkSizeToView == 0) return;
    Socket s = new Socket();
    s.connect(addr, HdfsConstants.READ_TIMEOUT);
    s.setSoTimeout(HdfsConstants.READ_TIMEOUT);
      
      long amtToRead = Math.min(chunkSizeToView, blockSize - offsetIntoBlock);     
      
      // Use the block name for file name. 
      BlockReader blockReader = 
        BlockReader.newBlockReader(s, addr.toString() + ":" + blockId,
                                             blockId, accessToken, genStamp ,offsetIntoBlock, 
                                             amtToRead, 
                                             conf.getInt("io.file.buffer.size",
                                                         4096));
        
    byte[] buf = new byte[(int)amtToRead];
    int readOffset = 0;
    int retries = 2;
    while ( amtToRead > 0 ) {
      int numRead;
      try {
        numRead = blockReader.readAll(buf, readOffset, (int)amtToRead);
      }
      catch (IOException e) {
        retries--;
        if (retries == 0)
          throw new IOException("Could not read data from datanode");
        continue;
      }
      amtToRead -= numRead;
      readOffset += numRead;
    }
    blockReader = null;
    s.close();

    StringTokenizer st = new StringTokenizer(new String(buf), "\n", false);
    while (st.hasMoreTokens()) {
      String line = st.nextToken();
      out.print("<a class=prov href=\"http://" + namenodeHost + ":" + namenodeInfoPort
          + "/browseProvenance.jsp?filename=" + encodedFilename
          + "&offset=" + offsetIntoBlock + "\">");
      out.print(HtmlQuoting.quoteHtmlChars(line));
      out.print("</a>\n");
      offsetIntoBlock += line.length() + 1;
    }
  }

  public static void addTableHeader(JspWriter out) throws IOException {
    out.print("<table border=\"1\""+
              " cellpadding=\"2\" cellspacing=\"2\">");
    out.print("<tbody>");
  }
  public static void addTableRow(JspWriter out, String[] columns) throws IOException {
    out.print("<tr>");
    for (int i = 0; i < columns.length; i++) {
      out.print("<td style=\"vertical-align: top;\"><B>"+columns[i]+"</B><br></td>");
    }
    out.print("</tr>");
  }
  public static void addTableRow(JspWriter out, String[] columns, int row) throws IOException {
    out.print("<tr>");
      
    for (int i = 0; i < columns.length; i++) {
      if (row/2*2 == row) {//even
        out.print("<td style=\"vertical-align: top;background-color:LightGrey;\"><B>"+columns[i]+"</B><br></td>");
      } else {
        out.print("<td style=\"vertical-align: top;background-color:LightBlue;\"><B>"+columns[i]+"</B><br></td>");
          
      }
    }
    out.print("</tr>");
  }
  public static void addTableFooter(JspWriter out) throws IOException {
    out.print("</tbody></table>");
  }

  public static void sortNodeList(ArrayList<DatanodeDescriptor> nodes,
                           String field, String order) {
        
    class NodeComapare implements Comparator<DatanodeDescriptor> {
      static final int 
        FIELD_NAME              = 1,
        FIELD_LAST_CONTACT      = 2,
        FIELD_BLOCKS            = 3,
        FIELD_CAPACITY          = 4,
        FIELD_USED              = 5,
        FIELD_PERCENT_USED      = 6,
        FIELD_NONDFS_USED       = 7,
        FIELD_REMAINING         = 8,
        FIELD_PERCENT_REMAINING = 9,
        SORT_ORDER_ASC          = 1,
        SORT_ORDER_DSC          = 2;

      int sortField = FIELD_NAME;
      int sortOrder = SORT_ORDER_ASC;
            
      public NodeComapare(String field, String order) {
        if (field.equals("lastcontact")) {
          sortField = FIELD_LAST_CONTACT;
        } else if (field.equals("capacity")) {
          sortField = FIELD_CAPACITY;
        } else if (field.equals("used")) {
          sortField = FIELD_USED;
        } else if (field.equals("nondfsused")) {
          sortField = FIELD_NONDFS_USED;
        } else if (field.equals("remaining")) {
          sortField = FIELD_REMAINING;
        } else if (field.equals("pcused")) {
          sortField = FIELD_PERCENT_USED;
        } else if (field.equals("pcremaining")) {
          sortField = FIELD_PERCENT_REMAINING;
        } else if (field.equals("blocks")) {
          sortField = FIELD_BLOCKS;
        } else {
          sortField = FIELD_NAME;
        }
                
        if (order.equals("DSC")) {
          sortOrder = SORT_ORDER_DSC;
        } else {
          sortOrder = SORT_ORDER_ASC;
        }
      }

      public int compare(DatanodeDescriptor d1,
                         DatanodeDescriptor d2) {
        int ret = 0;
        switch (sortField) {
        case FIELD_LAST_CONTACT:
          ret = (int) (d2.getLastUpdate() - d1.getLastUpdate());
          break;
        case FIELD_CAPACITY:
          long  dlong = d1.getCapacity() - d2.getCapacity();
          ret = (dlong < 0) ? -1 : ((dlong > 0) ? 1 : 0);
          break;
        case FIELD_USED:
          dlong = d1.getDfsUsed() - d2.getDfsUsed();
          ret = (dlong < 0) ? -1 : ((dlong > 0) ? 1 : 0);
          break;
        case FIELD_NONDFS_USED:
          dlong = d1.getNonDfsUsed() - d2.getNonDfsUsed();
          ret = (dlong < 0) ? -1 : ((dlong > 0) ? 1 : 0);
          break;
        case FIELD_REMAINING:
          dlong = d1.getRemaining() - d2.getRemaining();
          ret = (dlong < 0) ? -1 : ((dlong > 0) ? 1 : 0);
          break;
        case FIELD_PERCENT_USED:
          double ddbl =((d1.getDfsUsedPercent())-
                        (d2.getDfsUsedPercent()));
          ret = (ddbl < 0) ? -1 : ((ddbl > 0) ? 1 : 0);
          break;
        case FIELD_PERCENT_REMAINING:
          ddbl =((d1.getRemainingPercent())-
                 (d2.getRemainingPercent()));
          ret = (ddbl < 0) ? -1 : ((ddbl > 0) ? 1 : 0);
          break;
        case FIELD_BLOCKS:
          ret = d1.numBlocks() - d2.numBlocks();
          break;
        case FIELD_NAME: 
          ret = d1.getHostName().compareTo(d2.getHostName());
          break;
        }
        return (sortOrder == SORT_ORDER_DSC) ? -ret : ret;
      }
    }
        
    Collections.sort(nodes, new NodeComapare(field, order));
  }

  public static void printPathWithLinks(String dir, JspWriter out, 
                                        int namenodeInfoPort,
                                        String tokenString
                                        ) throws IOException {
    try {
      String[] parts = dir.split(Path.SEPARATOR);
      StringBuilder tempPath = new StringBuilder(dir.length());
      out.print("<a href=\"browseDirectory.jsp" + "?dir="+ Path.SEPARATOR
          + "&namenodeInfoPort=" + namenodeInfoPort
          + "\">" + Path.SEPARATOR + "</a>");
      tempPath.append(Path.SEPARATOR);
      for (int i = 0; i < parts.length-1; i++) {
        if (!parts[i].equals("")) {
          tempPath.append(parts[i]);
          out.print("<a href=\"browseDirectory.jsp" + "?dir="
              + tempPath.toString() + "&namenodeInfoPort=" + namenodeInfoPort
              + SET_DELEGATION + tokenString);
          out.print("\">" + parts[i] + "</a>" + Path.SEPARATOR);
          tempPath.append(Path.SEPARATOR);
        }
      }
      if(parts.length > 0) {
        out.print(parts[parts.length-1]);
      }
    }
    catch (UnsupportedEncodingException ex) {
      ex.printStackTrace();
    }
  }

  public static void printGotoForm(JspWriter out,
                                   int namenodeInfoPort,
                                   String tokenString,
                                   String file) throws IOException {
    out.print("<form action=\"browseDirectory.jsp\" method=\"get\" name=\"goto\">");
    out.print("Goto : ");
    out.print("<input name=\"dir\" type=\"text\" width=\"50\" id\"dir\" value=\""+ file+"\">");
    out.print("<input name=\"go\" type=\"submit\" value=\"go\">");
    out.print("<input name=\"namenodeInfoPort\" type=\"hidden\" "
        + "value=\"" + namenodeInfoPort  + "\">");
    out.print("<input name=\"" + DELEGATION_PARAMETER_NAME +
              "\" type=\"hidden\" value=\"" + tokenString + "\">");
    out.print("</form>");
  }
  
  public static void createTitle(JspWriter out, 
                                 HttpServletRequest req, 
                                 String  file) throws IOException{
    if(file == null) file = "";
    int start = Math.max(0,file.length() - 100);
    if(start != 0)
      file = "..." + file.substring(start, file.length());
    out.print("<title>HDFS:" + file + "</title>");
  }

  /** Convert a String to chunk-size-to-view. */
  public static int string2ChunkSizeToView(String s, int defaultValue) {
    int n = s == null? 0: Integer.parseInt(s);
    return n > 0? n: defaultValue;
  }

  /** Return a table containing version information. */
  public static String getVersionTable() {
    return "<div id='dfstable'><table>"       
        + "\n  <tr><td id='col1'>Version:</td><td>" + VersionInfo.getVersion() + ", " + VersionInfo.getRevision()
        + "\n  <tr><td id='col1'>Compiled:</td><td>" + VersionInfo.getDate() + " by " + VersionInfo.getUser() + " from " + VersionInfo.getBranch()
        + "\n</table></div>";
  }

  /**
   * Validate filename. 
   * @return null if the filename is invalid.
   *         Otherwise, return the validated filename.
   */
  public static String validatePath(String p) {
    return p == null || p.length() == 0?
        null: new Path(p).toUri().getPath();
  }

  /**
   * Validate a long value. 
   * @return null if the value is invalid.
   *         Otherwise, return the validated Long object.
   */
  public static Long validateLong(String value) {
    return value == null? null: Long.parseLong(value);
  }

  /**
   * Validate a URL.
   * @return null if the value is invalid.
   *         Otherwise, return the validated URL String.
   */
  public static String validateURL(String value) {
    try {
      return URLEncoder.encode(new URL(value).toString(), "UTF-8");
    } catch (IOException e) {
      return null;
    }
  }
  
  /**
   * If security is turned off, what is the default web user?
   * @param conf the configuration to look in
   * @return the remote user that was configuration
   */
  public static UserGroupInformation getDefaultWebUser(Configuration conf
                                                       ) throws IOException {
    String[] strings = conf.getStrings(JspHelper.WEB_UGI_PROPERTY_NAME);
    if (strings == null || strings.length == 0) {
      throw new IOException("Cannot determine UGI from request or conf");
    }
    return UserGroupInformation.createRemoteUser(strings[0]);
  }

  /**
   * Get {@link UserGroupInformation} and possibly the delegation token out of
   * the request.
   * @param request the http request
   * @return a new user from the request
   * @throws AccessControlException if the request has no token
   */
  public static UserGroupInformation getUGI(HttpServletRequest request,
                                            Configuration conf
                                           ) throws IOException {
    UserGroupInformation ugi = null;
    if(UserGroupInformation.isSecurityEnabled()) {
      String user = request.getRemoteUser();
      String tokenString = request.getParameter(DELEGATION_PARAMETER_NAME);
      if (tokenString != null) {
        Token<DelegationTokenIdentifier> token = 
          new Token<DelegationTokenIdentifier>();
        token.decodeFromUrlString(tokenString);
        ugi = UserGroupInformation.createRemoteUser(user);
        ugi.addToken(token);
        ugi.setAuthenticationMethod(AuthenticationMethod.TOKEN);
      } else {
        if(user == null) {
          throw new IOException("Security enabled but user not " +
                                "authenticated by filter");
        }
        ugi = UserGroupInformation.createRemoteUser(user);
        ugi.setAuthenticationMethod(AuthenticationMethod.KERBEROS_SSL);
      }
    } else { // Security's not on, pull from url
      String user = request.getParameter("ugi");
      
      if(user == null) { // not specified in request
        ugi = getDefaultWebUser(conf);
      } else {
        ugi = UserGroupInformation.createRemoteUser(user);
      }
      ugi.setAuthenticationMethod(AuthenticationMethod.SIMPLE);
    }
    
    if(LOG.isDebugEnabled())
      LOG.debug("getUGI is returning: " + ugi.getShortUserName());
    return ugi;
  }


}
