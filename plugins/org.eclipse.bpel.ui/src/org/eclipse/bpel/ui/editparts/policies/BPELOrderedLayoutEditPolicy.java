/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.bpel.ui.editparts.policies;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.bpel.common.ui.layouts.AlignedFlowLayout;
import org.eclipse.bpel.ui.BPELUIPlugin;
import org.eclipse.bpel.ui.GraphicalBPELRootEditPart;
import org.eclipse.bpel.ui.IBPELUIConstants;
import org.eclipse.bpel.ui.commands.InsertInContainerCommand;
import org.eclipse.bpel.ui.commands.ReorderInContainerCommand;
import org.eclipse.bpel.ui.commands.SetNameAndDirectEditCommand;
import org.eclipse.bpel.ui.editparts.BPELEditPart;
import org.eclipse.bpel.ui.editparts.CaseEditPart;
import org.eclipse.bpel.ui.editparts.CollapsableEditPart;
import org.eclipse.bpel.ui.editparts.CompositeActivityEditPart;
import org.eclipse.bpel.ui.editparts.ProcessEditPart;
import org.eclipse.bpel.ui.figures.CenteredConnectionAnchor;
import org.eclipse.draw2d.ConnectionAnchor;
import org.eclipse.draw2d.FlowLayout;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.LayoutManager;
import org.eclipse.draw2d.ManhattanConnectionRouter;
import org.eclipse.draw2d.PolygonDecoration;
import org.eclipse.draw2d.PolylineConnection;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPolicy;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.LayerConstants;
import org.eclipse.gef.Request;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;
import org.eclipse.gef.editpolicies.FlowLayoutEditPolicy;
import org.eclipse.gef.requests.CreateRequest;
import org.eclipse.gef.requests.DropRequest;
import org.eclipse.gef.requests.GroupRequest;
import org.eclipse.swt.graphics.Color;


public class BPELOrderedLayoutEditPolicy extends FlowLayoutEditPolicy {

	ArrayList polyLineConnectionList = new ArrayList();	
	
	// colour of the connection lines
	Color arrowColor = BPELUIPlugin.getPlugin().getColorRegistry().get(IBPELUIConstants.COLOR_IMPLICIT_LINK);
	
	protected Command createAddCommand(EditPart child, EditPart before) {
		return new InsertInContainerCommand((EObject)getHost().getModel(), (EObject)child.getModel(), 
			(before == null)? null : (EObject)before.getModel());
	}

	protected Command createMoveChildCommand(EditPart child, EditPart before) {
		return new ReorderInContainerCommand((EObject)getHost().getModel(), (EObject)child.getModel(),
				(before == null)? null : (EObject)before.getModel());
	}
	
	protected Command getCreateCommand(CreateRequest request) {
		EditPart before = getInsertionReference(request);
		EObject parent = (EObject) getHost().getModel();
		EObject child = (EObject) request.getNewObject();
		EObject beforeObject = (EObject)(before != null ? before.getModel() : null);

		CompoundCommand command = new CompoundCommand();
		command.add(new InsertInContainerCommand(parent, child, beforeObject));

		command.add(new SetNameAndDirectEditCommand(child, getHost().getViewer()));
		return command;
	}

	protected Command getDeleteDependantCommand(Request request) {
		return null;
	}
	
	/**
	 * Returns the part that we should insert before.
	 * If request is null insert at the end of the list.
	 */
	protected EditPart getInsertionReference(Request request) {
		// TODO: what is this for?
		if (request instanceof DropRequest) {
			Point pt = ((DropRequest)request).getLocation();
			if (pt == null)	return null;
			return super.getInsertionReference(request);
		}
		return null;
	}

	public void refreshConnections() {	
		// remove connections before redrawing
		clearConnections();
		
		if (hasChildren() && !isCollapsed()) {
			if (isHorizontal()) {
				polyLineConnectionList = createHorizontalConnections((BPELEditPart)getHost());			
			} else {
				polyLineConnectionList = createVerticalConnections((BPELEditPart)getHost());			
			}
		}
	}
	
	public void clearConnections() {
		for (int i = 0; i < polyLineConnectionList.size(); i++) {			
			getLayer(LayerConstants.CONNECTION_LAYER).remove(((PolylineConnection)polyLineConnectionList.get(i)));
		}	
		polyLineConnectionList.clear();		
	}
	
	// return implicit links for a Horizontal edit part (e.g. a Switch).
	protected ArrayList createHorizontalConnections(BPELEditPart parent) {
		ArrayList connections = new ArrayList();
		List children = getConnectionChildren(parent);
		BPELEditPart sourcePart, targetPart;
		ConnectionAnchor sourceAnchor, targetAnchor;
		
		sourcePart = parent;
		sourceAnchor = sourcePart.getConnectionAnchor(CenteredConnectionAnchor.TOP_INNER);
		
		if (children != null){
			for (int i = 0; i < children.size(); i++) {
				targetPart = (BPELEditPart)children.get(i);
				targetAnchor = targetPart.getConnectionAnchor(CenteredConnectionAnchor.TOP);
				connections.add(createConnection(sourceAnchor,targetAnchor,arrowColor));
			}			
		}		
		return connections;
	}
	
	// return list of children to create vertical connections for.
	protected List getConnectionChildren(BPELEditPart editPart) {
		return editPart.getChildren();
	}
	
	// return implicit links for a Vertical edit part (e.g. a Sequence).
	protected ArrayList createVerticalConnections(BPELEditPart parent) {
		ArrayList connections = new ArrayList();
		List children = getConnectionChildren(parent);
		BPELEditPart sourcePart, targetPart;
		ConnectionAnchor sourceAnchor = null, targetAnchor = null;
		
		// TODO: Connections misaligned when there are no children
		if (!children.isEmpty()) {
			for (int i = 0; i <= children.size(); i++) {
				if (i == 0) {
					if (hasTopParentLink()) {
						sourcePart = parent;
						sourceAnchor = sourcePart.getConnectionAnchor(CenteredConnectionAnchor.TOP_INNER);
					} else {
						sourceAnchor = null;
					}
				} else {
					sourcePart = (BPELEditPart)children.get(i-1);
					sourceAnchor = sourcePart.getConnectionAnchor(CenteredConnectionAnchor.BOTTOM);
				}
				if (i == children.size()) {
					if (hasBottomParentLink()) {
						targetPart = parent;
						targetAnchor = targetPart.getConnectionAnchor(CenteredConnectionAnchor.BOTTOM_INNER);
					} else {
						targetAnchor = null;
					}
				} else {
					targetPart = (BPELEditPart)children.get(i);
					targetAnchor = targetPart.getConnectionAnchor(CenteredConnectionAnchor.TOP);
				}
				if (sourceAnchor != null && targetAnchor != null) {
					connections.add(createConnection(sourceAnchor,targetAnchor,arrowColor));
				}
			}
		}
	
		return connections;
	}
	
	protected boolean hasTopParentLink() {
		return !(getHost() instanceof ProcessEditPart);
	}
	
	protected boolean hasBottomParentLink() {
		if (getHost() instanceof ProcessEditPart)
			return false;
		if (getHost() instanceof CaseEditPart)
			return false;
		return true;
	}
	
	protected List getSourceParts(Request request) {
		List list = new ArrayList();
		if (request instanceof CreateRequest) {
			list.add(((CreateRequest) request).getNewObject());
		} else if (request instanceof GroupRequest) {
			List l = ((GroupRequest) request).getEditParts();
			for (Iterator e = l.iterator(); e.hasNext();) {
				EditPart editPart = (EditPart) e.next();				
				list.add(editPart.getModel());
			}
		}
		return list;
	}

	protected LayoutManager getLayoutManager() {
		if (getHost() instanceof GraphicalEditPart) {
			IFigure figure = ((GraphicalEditPart)getHost()).getContentPane();
			if (figure != null) return figure.getLayoutManager();
		}
		return null;
	}
	
	protected boolean canShowFeedback() {
		LayoutManager layout = getLayoutManager();
		if (layout == null) return false;
		if (layout instanceof FlowLayout) return true;
		if (layout instanceof AlignedFlowLayout) return true;
		return false;
	}

	protected void showLayoutTargetFeedback(Request request) {
		if (!canShowFeedback()) return;

		super.showLayoutTargetFeedback(request);
	}

	public EditPart getTargetEditPart(Request request) {
		// this policy only works for the BPEL Editor itself, not the outline
		// TODO: we shouldn't even install this for the outline!! (oops)
		if (getHost().getRoot() instanceof GraphicalBPELRootEditPart) {
			return super.getTargetEditPart(request);
		}
		return null;
	}

	/** 
	 * override to prevent any child policies from being installed
	 * 
	 * TODO: re-think this.
	 */
	protected EditPolicy createChildEditPolicy(EditPart child) {		
		return null;
	}

	protected boolean isHorizontal() {
		LayoutManager layout = getLayoutManager();
		if (layout instanceof FlowLayout) return ((FlowLayout)layout).isHorizontal();
		if (layout instanceof AlignedFlowLayout) return ((AlignedFlowLayout)layout).isHorizontal();
		return false;
	}
	
	protected boolean isCollapsed() {	
		if (getHost() instanceof CollapsableEditPart) {
			return ((CollapsableEditPart)getHost()).isCollapsed();
		}
		return false;
	}

	protected PolylineConnection createConnection(ConnectionAnchor sourceAnchor, ConnectionAnchor targetAnchor, Color color) {
		PolylineConnection connection = new PolylineConnection();
		connection.setSourceAnchor(sourceAnchor);
		connection.setTargetAnchor(targetAnchor);
		connection.setForegroundColor(color);
		connection.setBackgroundColor(color);
		connection.setConnectionRouter(new ManhattanConnectionRouter());
		PolygonDecoration arrow = new PolygonDecoration();
		arrow.setTemplate(PolygonDecoration.TRIANGLE_TIP);
		arrow.setScale(5,2.5);
		arrow.setBackgroundColor(arrowColor);
		connection.setTargetDecoration(arrow);
		getLayer(LayerConstants.CONNECTION_LAYER).add(connection);			
		return connection;
	}

	public void showTargetFeedback(Request request) {
        // don't bother if request can't be executed
        if (getHost() instanceof BPELEditPart) {
            if (!((BPELEditPart)getHost()).canExecuteRequest(request)) {
                return;
            }
        }
		super.showTargetFeedback(request);
	}
	
	/**
	 * Does the edit part have children? If so, implicit connection logic will be
	 * executed. The only edit parts which have children are CompositeActivityEditParts
	 * (Sequence, While, Flow, RepeatUntil, etc.) and CaseEditPart (Case, OnMessage, OnAlarm).
	 */
	protected boolean hasChildren() {
		EditPart host = getHost();
		if (host instanceof CaseEditPart || host instanceof CompositeActivityEditPart || host instanceof ProcessEditPart) {
			return true;
		}
		return false;
	}
}