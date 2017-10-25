package org.findroid.data_types;

import java.util.ArrayList;

import org.findroid.activities.R;
import org.finroc.core.FrameworkElement;
import org.finroc.core.datatype.CoreNumber;
import org.finroc.core.port.Port;
import org.finroc.core.port.PortCreationInfo;
import org.finroc.core.port.PortFlags;
import org.finroc.core.port.cc.PortNumeric;
import org.finroc.plugins.data_types.Angle;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class MultiJoystick extends View {
	
	// =======================================
	// MEMBER - VARIABLES
	// =======================================	
	private final int BLACK = 0xff000000;
	private final int RED = 0xffff0000;
	private final int RADIUS = 15;
			
	private Paint Top_Paint;
	private Paint Bottom_Paint; 
	private Paint BLACK_PAINT;
	private boolean excavator_mode;
	private boolean forklift_mode;
	private boolean pitch_lock;
			
	private int numberOfSticks;
	private ArrayList<Stick> sticks;
	private ArrayList<Stick> focusedSticks;
	private Stick tcp;
	private Stick pitch;
	
	private boolean initialized = false;
	private boolean reset = false;
	private boolean inverse = false;
	
	private float x_max;
	private float x_min;
	private float y_max;
	private float y_min;
	
	private FrameworkElement parent;
	private PortNumeric<Double> portX;
	private PortNumeric<Double> portY;
	private Port<Angle> portAngle;
	
	private String portXId;
	private String portYId;
	private String portAngleId;
	
	
	// =======================================
	// CONSTRUCTORS
	// =======================================
	public MultiJoystick(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		TypedArray typarr = ctx.getTheme().obtainStyledAttributes(attrs, 
				R.styleable.Joystick, 0, 0);
		try {
			numberOfSticks = typarr.getInt(R.styleable.Joystick_numberOfSticks, 1);
		} finally {
			typarr.recycle();
		}
		sticks = new ArrayList<Stick>(numberOfSticks);
		focusedSticks = new ArrayList<Stick>(numberOfSticks);
	}
	
	
	// =======================================
	// ANDROID - CALLBACKS
	// =======================================
	@Override
	public void onDraw(Canvas cnv) {
		if (!initialized || reset) {
			initPaints();
			initSticks();
			initialized = true;
			reset = false;
			
			if (forklift_mode) {
				tcp = getSticks().get(0);
				tcp.setX(( (-1) * x_min * getWidth()) / (x_max - x_min) );
				tcp.setY(( (-1) * y_min * getHeight()) / (y_max - y_min) );
				tcp.updateRectF();
			}
			tcp = excavator_mode ? getSticks().get(1) : null;
			tcp = forklift_mode ? getSticks().get(0) : tcp;
			pitch = excavator_mode ? getSticks().get(0) : null;
			
		}
		
		drawBackground(cnv, excavator_mode || forklift_mode);
		
		for (Stick s : sticks) {
			cnv.drawRoundRect(s.getRectF(), RADIUS, RADIUS, s.getPaint());
		}
	}
	
	public void drawBackground(Canvas cnv, boolean back) {
		if (back) {
			float x_origin = x_min == 0 ? 1 : (x_max - x_min) / Math.abs(x_min);
			float y_origin = y_min == 0 ? 1 : (y_max - y_min) / Math.abs(y_min);
			
			// Paint the Origin in X-direction
			if (x_max > 0 && x_min < 0) {
				// Paint the Origin in Y-direction
				if (y_max > 0 && y_min < 0) {
					if (inverse) {
						cnv.drawRect(0, 0, getWidth() - getWidth()/x_origin, getHeight() - getHeight()/y_origin, Top_Paint);
						cnv.drawRect(getWidth() - getWidth()/x_origin + 1, 0, getWidth(), getHeight() - getHeight()/y_origin, Top_Paint);
						cnv.drawRect(0, getHeight() - getHeight()/y_origin + 1, getWidth() - getWidth()/x_origin, getHeight(), Bottom_Paint);
						cnv.drawRect(getWidth() - getWidth()/x_origin + 1, getHeight() - getHeight()/y_origin + 1, getWidth(), getHeight(), Bottom_Paint);
					} else {
						cnv.drawRect(0, 0, getWidth()/x_origin, getHeight() - getHeight()/y_origin, Top_Paint);
						cnv.drawRect(getWidth()/x_origin + 1, 0, getWidth(), getHeight() - getHeight()/y_origin, Top_Paint);
						cnv.drawRect(0, getHeight() - getHeight()/y_origin + 1, getWidth()/x_origin, getHeight(), Bottom_Paint);
						cnv.drawRect(getWidth()/x_origin + 1, getHeight() - getHeight()/y_origin + 1, getWidth(), getHeight(), Bottom_Paint);
					}
				}
				// Don't Paint the Origin in Y-direction
				else {
					if (inverse) {
						cnv.drawRect(0, 0, getWidth() - getWidth()/x_origin, getHeight(), y_max > 0 ? Top_Paint : Bottom_Paint);
						cnv.drawRect(getWidth() - getWidth()/x_origin + 1, 0, getWidth(), getHeight(), y_max > 0 ? Top_Paint : Bottom_Paint);
					} else {
						cnv.drawRect(0, 0, getWidth()/x_origin, getHeight(), y_max > 0 ? Top_Paint : Bottom_Paint);
						cnv.drawRect(getWidth()/x_origin + 1, 0, getWidth(), getHeight(), y_max > 0 ? Top_Paint : Bottom_Paint);
					}
				}
			}
			// Don't Paint the Origin in X-direction
			else {
				// Paint the Origin in Y-direction
				if (y_max > 0 && y_min < 0) {
					cnv.drawRect(0, 0, getWidth(), getHeight() - getHeight()/y_origin, Top_Paint);
					cnv.drawRect(0, getHeight() - getHeight()/y_origin + 1, getWidth(), getHeight(), Bottom_Paint);
				}
				// Don't Paint the Origin in Y-direction
				else {
					cnv.drawRect(0,0, getWidth(), getHeight(), y_max > 0 ? Top_Paint : Bottom_Paint);
				}
			}
			
		} else {
			cnv.drawLine(0, getHeight()/2 + 1, getWidth(), getHeight()/2 + 1, BLACK_PAINT);
			cnv.drawLine(getWidth()/2 + 1, 0, getWidth()/2 + 1, getHeight(), BLACK_PAINT);
		}
	}
	
	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// Try for a width based on our minimum
	   int minw = getSuggestedMinimumWidth() + getPaddingLeft() + getPaddingRight();
	   int w = resolveSizeAndState(minw, widthMeasureSpec, 1);

	   // Whatever the width ends up being, ask for a height that would let the pie
	   // get as big as it can
	   int h = resolveSizeAndState(MeasureSpec.getSize(w), heightMeasureSpec, 0);

	   setMeasuredDimension(w, h);	
	}
	
	@Override
	public void onSizeChanged(int w, int h, int oldw, int oldh) {	    
	    
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			
			// Finger starts touching the screen
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_POINTER_DOWN:
				for (Stick s : getSticks()) {
					if (distance(event.getX(getFocusedSticks().size()), s.getX(), 
							event.getY(getFocusedSticks().size()), s.getY()) <= 40.0 
							&& s.getID() == Stick.NOT_TOUCHED) {
						getFocusedSticks().add(s);
						s.setID(getFocusedSticks().indexOf(s));
						break;
					}
				}
				break;
			
			// Finger stops touching the screen
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP:
				if (forklift_mode) {
					float backX = inverse ? getWidth() - ( (-1) * x_min * getWidth()) / (x_max - x_min) : ((-1) * x_min * getWidth()) / (x_max - x_min);
					float backY = inverse ? getHeight() - ( (-1) * y_min * getHeight()) / (y_max - y_min) : ((-1) * y_min * getHeight()) / (y_max - y_min);
					tcp.setXY(backX, backY);
				}
				for (Stick s : getSticks()) {
					if (s.getID() == event.getActionIndex()) {
						getFocusedSticks().remove(s);
						updateStickIds();
						s.setID(Stick.NOT_TOUCHED);
						break;
					}
				}
				break;
				
			// Finger moves along the screen after touching it for the first time
			case MotionEvent.ACTION_MOVE:
				if (pitch_lock) {
					// tcp is being dragged -> don't move pitch
					if (tcp.getID() != Stick.NOT_TOUCHED && pitch.getID() == Stick.NOT_TOUCHED) {
						float x_offset = event.getX(tcp.getID()) - tcp.getX();
						float y_offset = event.getY(tcp.getID()) - tcp.getY();
						pitch.setXY(pitch.getX() + x_offset, pitch.getY() + y_offset);
						tcp.setXY(event.getX(tcp.getID()), event.getY(tcp.getID()));
					}
					// tcp isn't being dragged -> move pitch
					else if (tcp.getID() == Stick.NOT_TOUCHED && pitch.getID() != Stick.NOT_TOUCHED){
						pitch.setXY(event.getX(pitch.getID()), event.getY(pitch.getID()));
					}
				// no pitch lock
				} else {
					for (Stick s : getSticks()) {
						if (s.getID() != Stick.NOT_TOUCHED) {
							s.setXY(event.getX(s.getID()), event.getY(s.getID()));
						}
					}
				}
				break;
		}
		
		if (excavator_mode) {
    		portX.publish((double) toRobotX(getSticks().get(1)));
        	portY.publish((double) toRobotY(getSticks().get(1)));
        	Angle ang = portAngle.getUnusedBuffer();
        	ang.setRad(toAngle(getSticks().get(1), getSticks().get(0)));
        	portAngle.publish(ang);
    	} else if (forklift_mode) {
    		portX.publish((double) toRobotX(getSticks().get(0)));
    		portY.publish((double) toRobotY(getSticks().get(0)));
    	}
		
		invalidate(); // FORCES joystick to re-draw itself
		return true;
	}
	
	
	// =======================================
 	// ROBOT - SPECIFIC
 	// =======================================
 	public float toRobotX(Stick s) {
 		if (inverse) return x_min + (((x_max - x_min) / getWidth()) * (getWidth() - s.getX()));
 		else return x_min + (((x_max - x_min) / getWidth()) * s.getX());
 	}
 	
 	public float toRobotY(Stick s) {
 		return y_min + ((y_max - y_min) / getHeight()) * (getHeight() - s.getY());
 	}
 	
 	public float toAngle(Stick s1, Stick s2) {		 		
 		float x = Math.abs(s1.getX() - s2.getX());
 		float y = Math.abs(s1.getY() - s2.getY());
 		
 		if (s2.getX() < s1.getX()) {		
 			// 1.st quadrant
 			if (s2.getY() < s1.getY()) {
 				if (inverse) return (float) (Math.atan(y/x) + ((3.0*Math.PI) / 2.0));
 				else return (float) Math.atan(x/y);
 			} 
 			// 2.nd quadrant
 			else {
 				if (inverse) return (float) (Math.atan(x/y) + Math.PI);
 				else return (float) (Math.atan(y/x) + (Math.PI/2.0));
 			}
 		} else {
 			// 3.th quadrant
 			if (s2.getY() > s1.getY()) {
 				if (inverse) return (float) (Math.atan(y/x) + (Math.PI/2.0));
 				else return (float) (Math.atan(x/y) + Math.PI);
 			}
 			// 4.th quadrant
 			else {
 				if (inverse) return (float) Math.atan(x/y);
 				else return (float) (Math.atan(y/x) + ((3.0*Math.PI) / 2.0));
 			}
 		}	
 	}

	
	// =======================================
	// MISCELLANEOUS
	// =======================================	
	public void updateStickIds() {
		for (Stick s : focusedSticks) {
			s.setID(focusedSticks.indexOf(s));
		}
	}
	
	public double distance(float x1, float x2, float y1, float y2) {
		double x = Math.pow((x1 - x2), 2);
		double y = Math.pow((y1 - y2), 2);
		return Math.sqrt(x + y);
	}
	
	public void initPaints() {
		Top_Paint = new Paint();
		Top_Paint.setColor(0xff00bfff);
		Bottom_Paint = new Paint();
		Bottom_Paint.setColor(0xffd2691e);
		BLACK_PAINT = new Paint();
		BLACK_PAINT.setColor(BLACK);
	}
	
	public void initSticks() {
		sticks.clear();
		focusedSticks.clear();
		
		for (int i = 0; i < numberOfSticks; i++) {
			sticks.add(null);
		}
		
		if (excavator_mode) {			
			float y_tcp = (y_min > 0 || y_max < 0) ? getHeight()/2 : getHeight() - (float) (Math.abs(y_min) * getHeight()/Math.abs(y_max - y_min));
			float y_pitch = y_tcp - (Math.abs(y_max/2) * getHeight()/Math.abs(y_max - y_min));
			float x_both = (x_min > 0 || x_max < 0) ? getWidth()/2 : (float) (Math.abs(x_min) * getWidth()/Math.abs(x_max - x_min)) + (float) (Math.abs(x_max/2) * getWidth()/Math.abs(x_max - x_min));
			sticks.set(0, new Stick(Stick.NOT_TOUCHED, x_both, y_pitch, BLACK));
			sticks.set(1, new Stick(Stick.NOT_TOUCHED, x_both, y_tcp, RED));
		} else {
			for (int i = 0; i < sticks.size(); i++) {
	    		sticks.set(i, new Stick(Stick.NOT_TOUCHED, (i+1)*getWidth()/(sticks.size()+1), 
	    				getHeight()/2, BLACK));
	    	}
		}
	}
	
	public void initPorts() {
		portX = new PortNumeric<Double>(new PortCreationInfo("Joystick X", 
    			parent, CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
    	portY = new PortNumeric<Double>(new PortCreationInfo("Joystick Y", 
    			parent, CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
    	portAngle = new Port<Angle>(new PortCreationInfo("Joystick Angle", 
    			parent, Angle.TYPE, PortFlags.SHARED_OUTPUT_PORT));
    	portX.connectTo(portXId);
    	portY.connectTo(portYId);
    	portAngle.connectTo(portAngleId);
	}
	
	public void invertSticks() {
		for (Stick s : sticks) {
        	s.setXY(getWidth() - s.getX(), s.getY());
		}
	}
	
	
	// =======================================
	// GET & SET
	// =======================================
	public ArrayList<Stick> getSticks() {
		return sticks;
	}
	
	public void setSticks(ArrayList<Stick> stks) {
		sticks = stks;
	}
	
	public ArrayList<Stick> getFocusedSticks() {
		return focusedSticks;
	}
	
	public void setFocusedSticks(ArrayList<Stick> stks) {
		focusedSticks = stks;
	}
	
	public int getNumberOfSticks() {
		return numberOfSticks;
	}
	
	public void setNumberOfSticks(int nos) {
		numberOfSticks = nos;
	}
	
	public boolean getReset() {
		return reset;
	}
	
	public void setReset(boolean res) {
		reset = res;
	}
	
	public boolean getExcavatorMode() {
		return excavator_mode;
	}
	
	public void setExcavatorMode(boolean exc) {
		excavator_mode = exc;
	}
	
	public boolean getForkliftMode() {
		return forklift_mode;
	}
	
	public void setForkliftMode(boolean fork) {
		forklift_mode = fork;
	}
	
	public boolean getPitchLock() {
		return pitch_lock;
	}
	
	public void setPitchLock(boolean pl) {
		pitch_lock = pl;
	}
	
	public float getXMax() {
		return x_max;
	}
	
	public void setXMax(float x) {
		x_max = x;
	}
	
	public float getXMin() {
		return x_min;
	}
	
	public void setXMin(float x) {
		x_min = x;
	}
	
	public float getYMax() {
		return y_max;
	}
	
	public void setYMax(float y) {
		y_max = y;
	}
	
	public float getYMin() {
		return y_min;
	}
	
	public void setYMin(float y) {
		y_min = y;
	}
	
	public Stick getTcp() {
		return tcp;
	}
	
	public void setTcp(Stick t) {
		tcp = t;
	}
	
	public Stick getPitch() {
		return pitch;
	}
	
	public void setPitch(Stick p) {
		pitch = p;
	}
	
	public boolean getInverse() {
		return inverse;
	}
	
	public void setInverse(boolean i) {
		inverse = i;
	}
	
	public void setPortX(PortNumeric<Double> port) {
		portX = port;
	}
	
	public PortNumeric<Double> getPortX() {
		return portX;
	}
	
	public void setPortY(PortNumeric<Double> port) {
		portY = port;
	}
	
	public PortNumeric<Double> getPortY() {
		return portY;
	}
	
	public void setPortAngle(Port<Angle> port) {
		portAngle = port;
	}
	
	public Port<Angle> getPortAngle() {
		return portAngle;
	}
	
	public void setParentElement(FrameworkElement frame) {
		parent = frame;
	}
	
	public FrameworkElement getParentElement() {
		return parent;
	}
	
	public void setPortXId(String id) {
		portXId = id;
	}
	
	public String getPortXId() {
		return portXId;
	}
	
	public void setPortYId(String id) {
		portYId = id;
	}
	
	public String getPortYId() {
		return portYId;
	}
	
	public void setPortAngleId(String id) {
		portAngleId = id;
	}
	
	public String getPortAngleId() {
		return portAngleId;
	}
	
	// =======================================
	// INNER CLASS
	// =======================================
	class Stick {
		
		// =======================================
		// MEMBER - VARIABLES
		// =======================================
		public final static int NOT_TOUCHED = -1;

		private final int RADIUS = 15;
		
		private int touchId;
		private float xPos;
		private float yPos;
		private Paint paint;
		private RectF rectf;

		
		// =======================================
		// CONSTRUCTORS
		// =======================================
		public Stick(int id, float x, float y, int col) {
			touchId = id;
			xPos = x;
			yPos = y;
			paint = new Paint();
			paint.setColor(col);
			rectf = new RectF(x-RADIUS, y-RADIUS, x+RADIUS, y+RADIUS);
		}
		
		
		// =======================================
		// GET & SET
		// =======================================
		public int getID() {
			return touchId;
		}
		
		public void setID(int id) {
			touchId = id;
		}
		
		public float getX() {
			return xPos;
		}
		
		public void setX(float x) {
			xPos = x;
		}
		
		public float getY() {
			return yPos;
		}
		
		public void setY(float y) {
			yPos = y;
		}
		
		public Paint getPaint() {
			return paint;
		}
		
		public void setColor(int col) {
			paint.setColor(col);
		}
		
		public RectF getRectF() {
			return rectf;
		}
		
		public void setRectF(RectF rec) {
			rectf = rec;
		}
		
		public void updateRectF() {
			rectf = new RectF(xPos-RADIUS, yPos-RADIUS, xPos+RADIUS, yPos+RADIUS);
		}
		
		public void setXY(float x, float y) {
			xPos = x;
			yPos = y;
			updateRectF();
		}
			
	}
}
