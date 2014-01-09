import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;

import org.graphstream.algorithm.Dijkstra;
import org.graphstream.algorithm.Dijkstra.Element;
import org.graphstream.graph.Edge;
import org.graphstream.graph.EdgeRejectedException;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.*;
import org.graphstream.stream.file.FileSinkImages;
import org.graphstream.stream.file.FileSinkImages.LayoutPolicy;
import org.graphstream.stream.file.FileSinkImages.OutputType;
import org.graphstream.stream.file.FileSinkImages.Resolutions;
import org.graphstream.ui.swingViewer.View;
import org.graphstream.ui.swingViewer.Viewer;

/**
 * Class which defines all the methods concerning the creation and manipulation of the graph.
 *
 */
public class GraphClass 
{

	static SingleGraph graph;
	static SingleGraph pathGraph;
	static View view,viewPath;
	static String viewID;
    static int finishedThreads;
	/**
	* Gets the required paths from the database
	* @param version Version chosen by user
	* @param Region Region chosen by user
	* @param ISPName Name of ISP chosen by user
	* @param Status Status of data chosen by user
	 * @throws ClassNotFoundException Thrown if the H2 drivers aren't loaded.
	 * @throws SQLException Thrown if it the program is unable to connect to the database or execute the query.
	 * @throws NoResultException Thrown if no data is found.
	* 
	*/
	private static ResultSet GetAllPaths(int version, String Region, String ISPName, String Status) throws ClassNotFoundException, SQLException, NoResultException
    {
        Class.forName("org.h2.Driver");
        Connection conn = DriverManager.getConnection("jdbc:h2:~/COP", "sa", "");
        Statement stat = conn.createStatement();
        
        StringBuffer Query = new StringBuffer("select * from PathDB where Version="+version+" and ASName='"+ISPName+"' and Region='"+Region+"' and issent=");
        if(Status=="Sent")
        	Query.append("true");
        else
        	Query.append("false");
        
        ResultSet rs;
        rs = stat.executeQuery(Query.toString());
        
        if(rs==null)
        {
        	throw new NoResultException();
        }
        
        return rs;
    }
    
	/**
	* Gets the required paths from the database
	* @param results ResultSet from the query
	 * @throws NumberFormatException Thrown if a parsing error occurs.
	 * @throws SQLException Thrown if it the program is unable to connect to the database or execute the query.
	* 
	*/
    private static String[] GetPath(ResultSet results) throws NumberFormatException, SQLException
	{
    	int HopCount = Integer.parseInt(results.getString("Hops"));
    	
		String[] S = new String[HopCount];
		for(int i=1; i<=HopCount; i++)
		{
			S[i-1]=results.getString("Hop"+Integer.toString(i));
		}
		return S;
	}
    
    /**
	* Gets the ISP Name corresponding to an AS Number.
	* @param ASNum The required AS Number.
	 * @throws SQLException Thrown if it the program is unable to connect to the database or execute the query.
     * @throws ClassNotFoundException Thrown if the H2 drivers aren't loaded.
	* 
	*/
    private static String GetASName(String ASNum) throws SQLException, ClassNotFoundException
    {
    	Class.forName("org.h2.Driver");
        Connection conn = DriverManager.getConnection("jdbc:h2:~/COP", "sa", "");
        Statement stat = conn.createStatement();
        
        ResultSet rs;
        rs = stat.executeQuery("select * from ISPNames where ASNum='"+ASNum+"'");
        
        String R=new String();
        while(rs.next())
        {
        	R=rs.getString("ShortName");
        }
        return R;
   
    }
    
    /**
     * Takes a snapshot of the graph and stores as a .png file in the current directory.
     */
    static void SnapShot()
    {
  
    	FileSinkImages pic = new FileSinkImages(OutputType.PNG, Resolutions.VGA);
    	pic.setLayoutPolicy(LayoutPolicy.COMPUTED_FULLY_AT_NEW_IMAGE);
    	String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
    	try
    	{
    		pic.writeAll(graph, "screenshot"+timeStamp+".png");
    	}
    	catch(IOException e)
    	{
    		new NotifyUser("Cannot take screenshot now. File Error.");
    	}
    	
    	
    }
    
    /**
	* Creates the graph
	* @param version Version chosen by user
	* @param Region Region chosen by user
	* @param ISPName Name of ISP chosen by user
	* @param Status Status of data chosen by user
    * @throws ClassNotFoundException Thrown if the H2 drivers aren't loaded.
	 * @throws SQLException Thrown if it the program is unable to connect to the database or execute the query.
	 * @throws NoResultException Thrown if no data is found.
	 * @throws IOException Thrown if there is an error with the intermediate file.
	* 
	*/
    public static void createTree(int version, String Region, String ISPName, String Status) throws ClassNotFoundException, SQLException, NoResultException, IOException
    {
    	if(MainFrame.panel2.isAncestorOf(view))
    	{
    		MainFrame.panel2.remove(view);
    	}
    	graph = new SingleGraph(Region+" "+ISPName+" "+Status);
    	
    	computeGraph(graph, version, Region,  ISPName,  Status);
    	
		Viewer viewer = graph.display(false);
		viewer.enableAutoLayout();
		view = viewer.addDefaultView(false);  
		view.setSize(500, 400);

		view.addComponentListener(new ComponentListener()
				{
					public void componentResized(ComponentEvent arg0) {
						view.setSize(500, 400);
						view.setLocation(0, 0);
					}

					public void componentHidden(ComponentEvent arg0) {
						
					}

					public void componentMoved(ComponentEvent arg0) {
					}

					public void componentShown(ComponentEvent arg0) {
					}

					
				});
		if(viewPath!=null)
			viewPath.setVisible(false);
		MainFrame.panel2.add(view);
		
		MainFrame.panel3.setVisible(true);
		MainFrame.panel5.setVisible(false);
		MainFrame.zoomSlider.setValue(0);
		
		File f = new File("input.txt");
		f.delete();
		
		
	}
    
    /**
	* Creates the intermediate file
	* @param version Version chosen by user
	* @param Region Region chosen by user
	* @param ISPName Name of ISP chosen by user
	* @param Status Status of data chosen by user
     * @throws ClassNotFoundException Thrown if the H2 drivers aren't loaded.
	 * @throws SQLException Thrown if it the program is unable to connect to the database or execute the query.
	 * @throws NoResultException Thrown if no data is found.
	 * @throws IOException Thrown if there is an error with the intermediate file.
	 * 
	* 
	*/
	private static void createFile(int version, String Region, String ISPName, String Status) throws ClassNotFoundException, SQLException, NoResultException, IOException
    {

			Writer fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("input.txt"), "utf-8"));
	        StringBuffer PathString=new StringBuffer();
	   	    ResultSet rs=GetAllPaths(version, Region, ISPName, Status);
	   	    
			if(rs.next())
			{		
				do
				{
					String[] parts = GetPath(rs);			

				    for(int i=0;i<parts.length;i++)
				    {
				    	if(i!=0)
				    	PathString.append(" ");
				    	PathString.append(parts[i]);
				    }
				    PathString.append('\n');
				    fileWriter.write(PathString.toString());
				    PathString = new StringBuffer();
				    
				}
				while(rs.next());
				
			}
			//else	throw new NoResultException();
			
			fileWriter.close();
		
    }
    
    /**
     * 
     * @param source AS in the shortest path
     * @param dest - destination AS in the shortest path
     * uses Dijkstra's algorithm to compute the shortest path.
     * @throws IOException 
     * @throws NoResultException 
     * @throws SQLException 
     * @throws ClassNotFoundException 
     */
	
	public static void getShortestPath(String region,String source,String dest) throws ClassNotFoundException, SQLException, NoResultException, IOException
   // public static void main(String args[]) throws ClassNotFoundException, SQLException, NoResultException, IOException
    {
    	//String region = "Mumbai";
    	ArrayList<String> ISPs = new ArrayList<String>();
    	
		try {
			ISPs = MainFrame.getISPNames(region, null);
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace();
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		
     	int currentversion = MainFrame.getVersions();//gets the latest version of the database which we shall use for in this method.
    	pathGraph = new SingleGraph(region);
    	
		for(String ISPName : ISPs)
		{
			computeGraph( pathGraph, currentversion, region,  ISPName,  "Sent");	
			computeGraph( pathGraph, currentversion, region,  ISPName,  "Received");
		}
		
    	/*
		//TODO use multi-threading to reduce computation time.
    	finishedThreads = 0;
    	int numThreads =0;
    	for(String ISPName : ISPs)
    	{
    		numThreads+=2;
    		new GraphThread(pathGraph, currentversion, region,  ISPName,  "Sent");
    		new GraphThread(pathGraph, currentversion, region,  ISPName,  "Received");
    	}
    	while(finishedThreads!=numThreads);//looping till the entire graph is completeted
    	System.out.println("finished computing all threads ..graph ready");
    	*/
		
		File f = new File("input.txt");
		f.delete();
    	
       	Dijkstra dijkstra = new Dijkstra(Element.EDGE,"ui.label",null);
    	dijkstra.init(pathGraph);
    	//System.out.println(pathGraph.getNode(source));
    	dijkstra.setSource(pathGraph.getNode(source));
    	dijkstra.compute();
    	
    	//System.out.println("shortest path computed using dijkstra to "+pathGraph.getNode(dest));
    	//System.out.println("path length: "+dijkstra.getPathLength(pathGraph.getNode(dest)));
    	
    	Iterator<Edge> iter = dijkstra.getPathEdgesIterator(pathGraph.getNode(dest));
    	
    	SingleGraph displayPath = new SingleGraph(source+"-"+dest);
    	while(iter.hasNext())
    	{
    		Edge e = iter.next();
    		Node node0 = e.getNode0();
    		Node node1 = e.getNode1();
    		String id0 = node0.getId();
    		String id1 = node1.getId();
    		
    		if(displayPath.getNode(id0)==null) 
    			displayPath.addNode(id0);
    		if(displayPath.getNode(id1)==null)
    			displayPath.addNode(id1);
    		displayPath.getNode(id0).setAttribute("ui.label",node0.toString() );
    		displayPath.getNode(id1).setAttribute("ui.label",node1.toString() );
    		displayPath.getNode(id0).setAttribute("ui.style","fill-color: rgb(240,0,0);");
    		displayPath.getNode(id1).setAttribute("ui.style","fill-color: rgb(240,0,0);");
    		
    		
    		if(displayPath.getEdge(id0+"-"+id1)==null)
    		{
    			displayPath.addEdge(id0+"-"+id1,displayPath.getNode(id0),displayPath.getNode(id1));
    			displayPath.getEdge(id0+"-"+id1).addAttribute("ui.style", "size: 1;");    			
    			displayPath.getEdge(id0+"-"+id1).addAttribute("count",1);
    		}
    	}

    	Viewer viewer = displayPath.display(false);
		viewer.enableAutoLayout();
		if(view!=null)
			view.setVisible(false);
		
		viewPath = viewer.addDefaultView(false);
		viewPath.setSize(500, 400);
		viewPath.addComponentListener(new ComponentListener()
		{
			public void componentResized(ComponentEvent arg0) {
				viewPath.setSize(500, 400);
				viewPath.setLocation(0, 0);
			}

			public void componentHidden(ComponentEvent arg0) {
				
			}

			public void componentMoved(ComponentEvent arg0) {
			}

			public void componentShown(ComponentEvent arg0) {
			}

			
		});
		MainFrame.panel2.add(viewPath);
		MainFrame.panel3.setVisible(false);
		MainFrame.panel5.setVisible(true);
		MainFrame.zoomSlider1.setValue(0);
		
    }
/***
 * 
 * @param graph 
 * @param graph the computed graph. 
 * @param version the Database version number
 * @param Region - the NIXI region chosen
 * @param ISPName- the source in the graph.
 * @param Status whether we are analyzing sent/received data.
 * @throws IOException 
 * @throws NoResultException 
 * @throws SQLException 
 * @throws ClassNotFoundException 
 */
	static void computeGraph(SingleGraph graph, int version, String Region, String ISPName, String Status) 
			throws ClassNotFoundException, SQLException, NoResultException, IOException,EdgeRejectedException 
	{
		//System.out.println("calling method - GraphClass.computeGraph("+version+" "+ Region+" "+ ISPName+" "+Status);
    	createFile(version, Region,  ISPName,  Status);
    	String strLine;
		
    	FileInputStream fstream = new FileInputStream("input.txt");	    	
		DataInputStream in = new DataInputStream(fstream);
		@SuppressWarnings("resource")
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		
		graph.setAutoCreate(true);
		final double EdgeMultiple = 0.06; 
		
		if(graph.getNode(ISPName)==null)
		{	
			graph.addNode(ISPName);
			//System.out.println("added node "+ISPName);
		}
		
		graph.getNode(ISPName).addAttribute("ui.label", ISPName);
		graph.getNode(ISPName).addAttribute("ui.style", "fill-color: rgb(240,0,0);");
		int rank;
		
		while ((strLine = br.readLine()) != null) 
		{
			String[] parts = strLine.split(" ");
			String First = GetASName(parts[0]);
			
			if(graph.getNode(First)==null)
    		{
    			graph.addNode(First);
    			if(Status=="Sent")
    			{
    				try {
						graph.addEdge(ISPName+First, ISPName, First, true);
					} catch (EdgeRejectedException e) {
						//e.printStackTrace();
						//System.out.print("graph already has edge -"+graph.getEdge(ISPName+First)!=null);
						//System.out.println(e.getMessage());
					}
    			}
    			else
    			{
    				try {
						graph.addEdge(ISPName+First, First, ISPName, true);
					} catch (EdgeRejectedException e) {
						//e.printStackTrace();
						//System.out.print("graph already has edge -"+graph.getEdge(ISPName+First)!=null);
						//System.out.println(e.getMessage());
					}
    			}
    			graph.getNode(First).addAttribute("ui.style", "fill-color: rgb(240,0,0);");
    			graph.getNode(First).addAttribute("ui.label", First);
    		}
			
			rank=0;

		    for(int i=1;i<parts.length;i++)
		    {
		    	String current = GetASName(parts[i]);
		    	String previous = GetASName(parts[i-1]);
		    	if(current!=previous)
		    	{
		    		if(graph.getNode(current)==null)
		    		{
		    			
		    			graph.addNode(current);
		    			//System.out.println("added node "+current);
		    			rank++;
		    			graph.getNode(current).addAttribute("ui.label", current);
		    			graph.getNode(current).addAttribute("ui.style", "fill-color: rgb("+Integer.toString(240-(rank*60))+","+Integer.toString((rank*60))+","+Integer.toString((rank*60))+");");
		    		}
		    		
				    if(graph.getNode(previous)==null)
				    {
				    	graph.addNode(previous);
				    	//System.out.println("added node "+previous);
				    	rank++;
				    	graph.getNode(previous).addAttribute("ui.label", previous);
		    			graph.getNode(previous).addAttribute("ui.style", "fill-color: rgb("+Integer.toString(240-(rank*60))+","+Integer.toString((rank*60))+","+Integer.toString((rank*60))+");");
				    }
		    		
				    if(graph.getEdge(previous+"-"+current)==null)
				    {
				    	String EdgeName = previous+"-"+current;
				    	if(Status=="Sent")
				    	{
				    		try {
								graph.addEdge(EdgeName, previous, current, true);
								if(graph.getEdge(EdgeName)!=null)
									{
									graph.getEdge(EdgeName).addAttribute("ui.style", "size: "+ Double.toString(EdgeMultiple) +";");
									graph.getEdge(EdgeName).addAttribute("count", 1);
									}
						        
							} catch (EdgeRejectedException e) {
								//e.printStackTrace();
								//System.out.print("graph already has edge -"+graph.getEdge(EdgeName)!=null);
								//System.out.println(e.getMessage());
							}
				    	}
				    	else
				    	{
				    		try {
								graph.addEdge(EdgeName, current, previous, true);
								
								if(graph.getEdge(EdgeName)!=null){
								graph.getEdge(EdgeName).addAttribute("ui.style", "size: "+ Double.toString(EdgeMultiple) +";");
						        graph.getEdge(EdgeName).addAttribute("count", 1);
								}
								
							} catch (EdgeRejectedException e) {
								//e.printStackTrace();
								//System.out.print("graph already has edge -"+graph.getEdge(EdgeName)!=null);
								//System.out.println(e.getMessage());
							}
				    	}
				    	
				    }
				    else
				    {
				    	String EdgeName = previous+"-"+current;
				    	int EdgeCount = Integer.parseInt(graph.getEdge(EdgeName).getAttribute("count").toString());
				    	EdgeCount++;
				    	if(graph.getEdge(EdgeName)==null){
					    	graph.getEdge(EdgeName).setAttribute("count", EdgeCount);
					    	graph.getEdge(EdgeName).setAttribute("ui.style", "size: "+ Double.toString(EdgeMultiple*EdgeCount) +";");
					    }
				    	
				    }
		    	}
		    }
		}
	}

}
