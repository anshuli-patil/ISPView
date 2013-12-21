import java.awt.Dimension;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;

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
	static View view;
	static String viewID;
    
	/**
	* Gets the required paths from the database
	* @param Version Version chosen by user
	* @param Region Region chosen by user
	* @param ISPName Name of ISP chosen by user
	* @param Status Status of data chosen by user
	 * @throws ClassNotFoundException Thrown if the H2 drivers aren't loaded.
	 * @throws SQLException Thrown if it the program is unable to connect to the database or execute the query.
	 * @throws NoResultException Thrown if no data is found.
	* 
	*/
	private ResultSet GetAllPaths(String Version, String Region, String ISPName, String Status) throws ClassNotFoundException, SQLException, NoResultException
    {
        Class.forName("org.h2.Driver");
        Connection conn = DriverManager.getConnection("jdbc:h2:~/COP", "sa", "");
        Statement stat = conn.createStatement();
        
        StringBuffer Query = new StringBuffer("select * from PathDB where Version="+Version.toString()+" and ASName='"+ISPName+"' and Region='"+Region+"' and issent=");
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
    private String[] GetPath(ResultSet results) throws NumberFormatException, SQLException
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
    private String GetASName(String ASNum) throws SQLException, ClassNotFoundException
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
     * Sets the zoom of the viewer according the the argument
     * @param viewPercent Fraction of the viewer to be visible
     */
    static void Zoom(double viewPercent)
    {
		view.getCamera().setViewPercent(viewPercent);    	
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
	* @param Version Version chosen by user
	* @param Region Region chosen by user
	* @param ISPName Name of ISP chosen by user
	* @param Status Status of data chosen by user
    * @throws ClassNotFoundException Thrown if the H2 drivers aren't loaded.
	 * @throws SQLException Thrown if it the program is unable to connect to the database or execute the query.
	 * @throws NoResultException Thrown if no data is found.
	 * @throws IOException Thrown if there is an error with the intermediate file.
	* 
	*/
    public void createTree(String Version, String Region, String ISPName, String Status) throws ClassNotFoundException, SQLException, NoResultException, IOException
    {
    	if(MainFrame.panel2.isAncestorOf(view))
    	{
    		System.out.println("the main frame was the ancestor of the view..removing it!");
    		MainFrame.panel2.remove(view);
    	}

    	createFile(Version, Region,  ISPName,  Status);
    	String strLine;
		
    	FileInputStream fstream = new FileInputStream("input.txt");	    	
		DataInputStream in = new DataInputStream(fstream);
		@SuppressWarnings("resource")
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		
		graph = new SingleGraph(Region+" "+ISPName+" "+Status);
		
		
		graph.setAutoCreate(true);
		
		double EdgeMultiple = 0.06; 
		
		
		graph.addNode(ISPName);
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
    				graph.addEdge(ISPName+First, ISPName, First, true);
    			}
    			else
    			{
    				graph.addEdge(ISPName+First, First, ISPName, true);
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
		    			rank++;
		    			graph.getNode(current).addAttribute("ui.label", current);
		    			graph.getNode(current).addAttribute("ui.style", "fill-color: rgb("+Integer.toString(240-(rank*60))+","+Integer.toString((rank*60))+","+Integer.toString((rank*60))+");");
		    		}
		    		
				    if(graph.getNode(previous)==null)
				    {
				    	graph.addNode(previous);
				    	rank++;
				    	graph.getNode(previous).addAttribute("ui.label", previous);
		    			graph.getNode(previous).addAttribute("ui.style", "fill-color: rgb("+Integer.toString(240-(rank*60))+","+Integer.toString((rank*60))+","+Integer.toString((rank*60))+");");
				    }
		    		
				    if(graph.getEdge(previous+"-"+current)==null)
				    {
				    	String EdgeName = previous+"-"+current;
				    	if(Status=="Sent")
				    	{
				    		graph.addEdge(EdgeName, previous, current, true);
				    	}
				    	else
				    	{
				    		graph.addEdge(EdgeName, current, previous, true);
				    	}
				    	graph.getEdge(EdgeName).addAttribute("ui.style", "size: "+ Double.toString(EdgeMultiple) +";");
				        graph.getEdge(EdgeName).addAttribute("count", 1);
				    }
				    else
				    {
				    	String EdgeName = previous+"-"+current;
				    	int EdgeCount = Integer.parseInt(graph.getEdge(EdgeName).getAttribute("count").toString());
				    	EdgeCount++;
				    	graph.getEdge(EdgeName).setAttribute("count", EdgeCount);
				    	graph.getEdge(EdgeName).setAttribute("ui.style", "size: "+ Double.toString(EdgeMultiple*EdgeCount) +";");
				    	
				    }
		    	}
		    }
		}
		
		Viewer viewer = graph.display(false);
		viewer.enableAutoLayout();
		view = viewer.addDefaultView(false);  
		view.setSize(500, 400);
		view.setMinimumSize(new Dimension(500,400));
		view.addComponentListener(new ComponentListener()
				{
					@Override
					public void componentResized(ComponentEvent arg0) {
						view.setSize(500, 400);
						view.setLocation(0, 0);
						System.out.println(arg0.toString());
						
					}

					@Override
					public void componentHidden(ComponentEvent arg0) {
						// TODO Auto-generated method stub
						
					}

					@Override
					public void componentMoved(ComponentEvent arg0) {
						// TODO Auto-generated method stub
						
					}

					@Override
					public void componentShown(ComponentEvent arg0) {
						// TODO Auto-generated method stub
						
					}

					
				});
		MainFrame.panel2.add(view);
		
		
		
		MainFrame.panel3.setVisible(true);
		MainFrame.zoomSlider.setValue(0);
		
		File f = new File("input.txt");
		f.delete();
		
		
	}

    
    /**
	* Creates the intermediate file
	* @param Version Version chosen by user
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
    @SuppressWarnings("resource")
	private void createFile(String Version, String Region, String ISPName, String Status) throws ClassNotFoundException, SQLException, NoResultException, IOException
    {

			Writer fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("input.txt"), "utf-8"));
	        StringBuffer PathString=new StringBuffer();
	   	    ResultSet rs=GetAllPaths(Version, Region, ISPName, Status);
	   	    
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
			else
			{
				throw new NoResultException();
			}
			
			fileWriter.close();
		
    }	       
    

}
