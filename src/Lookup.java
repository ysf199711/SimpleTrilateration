//Sifan, Ye (sye8)
//Jun. 19, 2017

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.JFrame;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer.Optimum;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

import com.google.gson.Gson;
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import com.lemmingapex.trilateration.TrilaterationFunction;

import sye8.utils.Coordinate;
import sye8.utils.ImageUtils;
import sye8.utils.Maths;

/**
 * Servlet implementation class Lookup
 */
@WebServlet("/Lookup")
public class Lookup extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	JFrame frame = new JFrame("Debug");
	
	Coordinate[] oldCoords = new Coordinate[2];
	int coordCount = 0;
	double prevV = Double.NaN;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Lookup() {
        super();
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		StringBuilder stringBuilder = new StringBuilder();
	    BufferedReader reader = request.getReader();
	    String line;
	    while ((line = reader.readLine()) != null) {
	    	stringBuilder.append(line).append('\n');
	    }
	    reader.close();
	  
	    String json = stringBuilder.toString();
	    Gson gson = new Gson();
	    
	    Node[] nodes = gson.fromJson(json, Node[].class);
	    System.out.println(nodes.length);
	    
		double[][] positions = new double[nodes.length][2];
		double[] distances = new double[nodes.length];
		Coordinate loc = null;
		double xError = Double.NaN;
		double yError = Double.NaN;

		//Connect to DB
		Connection connection = (Connection) getServletContext().getAttribute("DBConnection");
		PreparedStatement[] preparedStates = new PreparedStatement[nodes.length];
		ResultSet[] resultSets = new ResultSet[nodes.length];
		for(int i = 0; i < nodes.length; i++){
			preparedStates[i] = null;
			resultSets[i] = null;
			try{
				preparedStates[i] = connection.prepareStatement("select major, minor, x, y from Nodes where Major=? and Minor=? limit 1");
				preparedStates[i].setString(1, nodes[i].getMajorStr());
				preparedStates[i].setString(2, nodes[i].getMinorStr());
				resultSets[i] = preparedStates[i].executeQuery();
				if(resultSets[i] != null && resultSets[i].next()){
					positions[i][0] = resultSets[i].getDouble("x");
					positions[i][1] = resultSets[i].getDouble("y");
					distances[i] = nodes[i].accuracy;
				}			
			}catch(SQLException e){
				e.printStackTrace();
				continue;
			}
		}
		
	    switch (nodes.length) {
			case 1:
				loc = new Coordinate(positions[0][0], positions[0][1]);
				xError = distances[0];
				yError = distances[1];
				break;	
			case 2:
				if(Maths.intersect(positions, distances)){
					Coordinate[] result = Maths.findCoord(positions, distances);
					double x = (result[0].x + result[1].x)/2;
					double y = (result[0].y + result[1].y)/2;
					xError = Math.abs(result[0].x-result[1].x);
					yError = Math.abs(result[0].x-result[1].x);	
					loc = new Coordinate(x, y);
				}else{
					loc = new Coordinate(Double.NaN, Double.NaN);
				}				
				break;
			default:			
				//Estimate User Location
				NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
				Optimum optimum = solver.solve();
				double[] centroid = optimum.getPoint().toArray();
				double[] standardDeviation = optimum.getSigma(0).toArray();				
				loc = new Coordinate(centroid[0], centroid[1]);
				//95% certainty
				xError = standardDeviation[0]*1.96;
				yError = standardDeviation[1]*1.96;
				break;
		}	
	    
	    //Drift Correction
//	    switch(coordCount){
//	    	case 0: 
//	    		oldCoords[0] = loc;
//	    		coordCount++;
//	    		break;
//	    	case 1:
//	    		prevV = Coordinate.distance(oldCoords[0], loc); //Since CoreLocation sends iBeacon data per second
//	    		oldCoords[1] = loc;
//	    		coordCount++;
//	    		break;
//	    	case 2:
//	    		Coordinate prevC = oldCoords[1];
//	    		double tempV = Coordinate.distance(prevC, loc);
//	    		System.out.println("TempV: " + tempV);
//	    		if(tempV > 10){
//	    			double dx = prevC.x - oldCoords[0].x;
//	    			double dy = prevC.y - oldCoords[0].y;
//	    			loc = new Coordinate(prevC.x + dx, prevC.y + dy);
//	    		}else{		
//		    		prevV = tempV;
//	    		}
//	    		oldCoords[0] = prevC;
//	    		oldCoords[1] = loc;
//	    		break;
//		}
	    
		//Debug
		frame.setVisible(true);
		frame.setSize(1190, 1000);
		
		Graphics g = frame.getGraphics();
		Image map = ImageUtils.loadImage("/Users/yesifan/Documents/workspace/Trilateration/Room.jpg");
		g.drawImage(map,0,0,null);
		g.setColor(Color.BLACK);
		for(int i = 0; i < nodes.length; i++){
			int x = (int)(positions[i][0]*100);
			int y = (int)(positions[i][1]*100);
			g.fillOval(x-3, y-3, 6, 6);
			g.drawString(nodes[i].getMinorStr(), x, y);
			g.drawOval(x-(int)(distances[i]*100), y-(int)(distances[i]*100), (int)(distances[i]*100)*2, (int)(distances[i]*100)*2);
		}
		
		System.out.println(loc);
		System.out.println(xError);
		System.out.println(yError);
		
		//Send String
		PrintWriter out = response.getWriter();
		if(!loc.isNull()){
			g.setColor(Color.BLUE);
			g.fillOval((int)(loc.x*100) - 5, (int)(loc.y*100) - 5, 10, 10);
			out.print(loc);
			if(!Double.isNaN(xError) && !Double.isNaN(yError)){
				g.setColor(new Color(66, 134, 244, 100));
				g.fillOval((int)(loc.x*100)-(int)(xError*100), (int)(loc.y*100)-(int)(yError*100), (int)(xError*100)*2, (int)(yError*100)*2);
				out.printf("\nError: x: %.4f, y: %.4f", xError, yError);
			}		
			out.close();
			out.flush();
		}else{
			switch(nodes.length){			
				case 1:
					out.print("Only one node found\n");
					out.print("You are " + distances[0] + "m from\n" + "x: " + positions[0][0] + " y: " + positions[0][1]);
					out.close();
					out.flush();
					break;
				default:
					out.print("Cannot Determine Location");
					out.close();
					out.flush();
					break;
			}
		}
		doGet(request, response);
	}

}
