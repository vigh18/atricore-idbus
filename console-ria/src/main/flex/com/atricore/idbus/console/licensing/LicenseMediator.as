/*
 * Atricore Console
 *
 * Copyright 2009-2010, Atricore Inc.
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

package com.atricore.idbus.console.licensing
{
import com.atricore.idbus.console.licensing.main.controller.GetLicenseCommand;
import com.atricore.idbus.console.licensing.main.controller.UpdateLicenseCommand;
import com.atricore.idbus.console.licensing.main.event.DataGridButtonEvent;
import com.atricore.idbus.console.licensing.main.model.LicenseProxy;
import com.atricore.idbus.console.licensing.main.view.LicensingPopUpManager;
import com.atricore.idbus.console.licensing.main.view.updatelicense.UpdateLicenseMediator;
import com.atricore.idbus.console.main.ApplicationFacade;
import com.atricore.idbus.console.main.DistributionContext;
import com.atricore.idbus.console.services.dto.FeatureType;
import com.atricore.idbus.console.services.dto.LicensedFeatureType;

import flash.events.Event;
import flash.events.MouseEvent;

import mx.collections.ArrayCollection;
import mx.events.FlexEvent;
import mx.resources.IResourceManager;
import mx.resources.ResourceManager;
import mx.utils.StringUtil;

import org.osmf.traits.IDisposable;
import org.puremvc.as3.interfaces.INotification;
import org.springextensions.actionscript.puremvc.interfaces.IIocFacade;
import org.springextensions.actionscript.puremvc.patterns.mediator.IocMediator;

public class LicenseMediator extends IocMediator implements IDisposable {


    private var _popupManager:LicensingPopUpManager;

    private var _licenseProxy:LicenseProxy;

    protected var resourceManager:IResourceManager = ResourceManager.getInstance();

    //commands
    private var _updateLicenseCommand:UpdateLicenseCommand;
    private var _getLicenseCommand:GetLicenseCommand;

    //mediators
    private var _updateLicenseMediator:UpdateLicenseMediator;

    [Bindable]
    public var _selectedFiles:ArrayCollection;

    private var _created:Boolean;    

    public function LicenseMediator(name:String = null, viewComp:LicenseView = null) {
        super(name, viewComp);
    }

    public function get popupManager():LicensingPopUpManager {
        return _popupManager;
    }

    public function set popupManager(value:LicensingPopUpManager):void {
        _popupManager = value;
    }

    override public function setViewComponent(viewComponent:Object):void {
        if (getViewComponent() != null) {
            var distributionContext:DistributionContext = iocFacade.container.getObject("distributionContext") as DistributionContext;
            if (distributionContext.distribution == "josso-ee")
                view.btnUpdateLicense.removeEventListener(MouseEvent.CLICK, handleUpdateLicenseButton);
        }

        (viewComponent as LicenseView).addEventListener(FlexEvent.CREATION_COMPLETE, creationCompleteHandler);

        super.setViewComponent(viewComponent);
    }

    private function creationCompleteHandler(event:Event):void {
        _created = true;
        
        popupManager.init(iocFacade, view);

        var distributionContext:DistributionContext = iocFacade.container.getObject("distributionContext") as DistributionContext;
        if (distributionContext.distribution == "josso-ee") {
            view.licenseInfo.paddingTop = 0;
            view.updateLicenseBox.includeInLayout = true;
            view.updateLicenseBox.visible = true;
            view.btnUpdateLicense.addEventListener(MouseEvent.CLICK, handleUpdateLicenseButton);
        }

        init();
    }

    private function init():void {
        (facade as IIocFacade).registerMediatorByConfigName(updateLicenseMediator.getConfigName());
        (facade as IIocFacade).registerCommandByConfigName(ApplicationFacade.UPDATE_LICENSE, updateLicenseCommand.getConfigName());
        if(!(facade as IIocFacade).hasCommand(getLicenseCommand.getConfigName())){
            (facade as IIocFacade).registerCommandByConfigName(ApplicationFacade.GET_LICENSE, getLicenseCommand.getConfigName());
        }
        if (_created) {
            /* Remove unused title in account management panel */
            view.titleDisplay.width = 0;
            view.titleDisplay.height = 0;
            sendNotification(ApplicationFacade.GET_LICENSE);
        }
        view.addEventListener ( DataGridButtonEvent.BUTTON_CLICKED, handleViewLicenseButtonClick );
    }

    override public function listNotificationInterests():Array {
        return [ ApplicationFacade.LICENSE_VIEW_SELECTED,
            UpdateLicenseCommand.SUCCESS,
            UpdateLicenseCommand.FAILURE,
            ApplicationFacade.DISPLAY_UPDATE_LICENSE,
            ApplicationFacade.DISPLAY_EULA_TEXT,
            GetLicenseCommand.SUCCESS,
            GetLicenseCommand.FAILURE];
    }

    override public function handleNotification(notification:INotification):void {
        switch (notification.getName()) {
            case UpdateLicenseCommand.SUCCESS :
                sendNotification(ApplicationFacade.GET_LICENSE);
                break;
            case UpdateLicenseCommand.FAILURE :
                handleActivationFailure(notification);
                break;
            case ApplicationFacade.LICENSE_VIEW_SELECTED:
                init();
                break;
            case ApplicationFacade.DISPLAY_UPDATE_LICENSE:
                popupManager.showUpdateLicenseWindow(notification);
                break;
            case ApplicationFacade.DISPLAY_EULA_TEXT:
                popupManager.showLicenseTextWindow(notification);
                break;
            case GetLicenseCommand.SUCCESS:
                displayLicenseInfo();
                break;
            case GetLicenseCommand.FAILURE:
                //TODO show error - this should not happen
                break;
        }
    }

    public function handleUpdateLicenseButton(event:Event):void {
        sendNotification(ApplicationFacade.DISPLAY_UPDATE_LICENSE);
    }

    public function handleActivationFailure(notification:INotification):void {
        var errMsg:String = notification.getBody() as String;
        sendNotification(ApplicationFacade.SHOW_ERROR_MSG, errMsg);
    }

//    public function handleViewEulaButton(event:Event):void {
//        sendNotification(ApplicationFacade.DISPLAY_EULA_TEXT, StringUtil.trim(_licenseProxy.license.eula));
//    }

    public function handleViewLicenseButtonClick(event:DataGridButtonEvent):void {
        var btnId:String = event.data as String;
        var tmpId:String;
        var tmpFeature:FeatureType;
        for each (var licFeature:LicensedFeatureType in _licenseProxy.license.licensedFeature) {
            for each (var feature:FeatureType in licFeature.feature) {
                tmpId = feature.group + feature.name;
                if(btnId == tmpId){
                   tmpFeature = feature;
                    break;
                }
            }
        }
        sendNotification(ApplicationFacade.DISPLAY_EULA_TEXT, tmpFeature);
    }

    public function displayLicenseInfo():void {
        var lblWidth:Number = 150;
        var textAreaWidth:Number = 600;
//        view.licenseInfo.removeAllElements();

        //LICENSE OWNER
        if(_licenseProxy.license.organization != null && _licenseProxy.license.organization.owner != null){
            view.licenseOwner.text = _licenseProxy.license.organization.owner;
            view.licenseOwnerBox.includeInLayout = true;
            view.licenseOwnerBox.visible = true;
        } else {
            view.licenseOwner.text = "";
            view.licenseOwnerBox.includeInLayout = false;
            view.licenseOwnerBox.visible = false;
        }

        //ORGANIZATION
        if(_licenseProxy.license.organization != null && _licenseProxy.license.organization.organizationName != null){
            view.licenseOrganization.text = _licenseProxy.license.organization.organizationName;
            view.licenseOrganizationBox.includeInLayout = true;
            view.licenseOrganizationBox.visible = true;
        } else {
            view.licenseOrganization.text = "";
            view.licenseOrganizationBox.includeInLayout = false;
            view.licenseOrganizationBox.visible = false;
        }

        //ISSUE DATE
        if (_licenseProxy.license.issueInstant != null) {
            view.licenseIssueDate.text = view.dateFormatter.format(_licenseProxy.license.issueInstant);
        }

        //EXPIRATION DATE
        if (_licenseProxy.license.expiresOn != null) {
            view.licenseExpirationDate.text = view.dateFormatter.format(_licenseProxy.license.expiresOn);
        } else {
            view.licenseExpirationDate.text = "Perpetual";
        }

        //GENERAL EULA
        if(_licenseProxy.license.eula != null) {
//            view.generalEula.width = textAreaWidth;
//            view.generalEula.height = 100;
            view.generalEula.text = StringUtil.trim(_licenseProxy.license.eula);
        }

        //FEATURES
//        view.features.width = textAreaWidth;
//        view.features.height = 200;

        //copy all features to array, in case we have more than one licensedFeature
        var featureArray:ArrayCollection = new ArrayCollection();
        for each (var licFeature:LicensedFeatureType in _licenseProxy.license.licensedFeature) {
            for each (var feature:FeatureType in licFeature.feature) {
                featureArray.addItem(feature);
            }
        }

        view.features.dataProvider = featureArray;

        //name
        //description
        //version range
        //license details button
        var btnId:String = feature.group + feature.name;
//        btn.addEventListener(MouseEvent.CLICK, handleViewLicenseButton);


                //feature license text
        if(feature.licenseText != null){
//                    btn.label = resourceManager.getString(AtricoreConsole.BUNDLE, 'licensing.viewlicense');
//                    btn.id = feature.group + feature.name;
//
//                    hgroup.addElement(btn);
//                    view.licenseInfo.addElement(hgroup);


        }
    }

    protected function get view():LicenseView
    {
        return viewComponent as LicenseView;
    }

//    protected function set view(amv:LicenseView):void
//    {
//        viewComponent = amv;
//    }

    public function set licenseProxy(value:LicenseProxy):void {
        _licenseProxy = value;
    }

    public function get updateLicenseCommand():UpdateLicenseCommand {
        return _updateLicenseCommand;
    }

    public function set updateLicenseCommand(value:UpdateLicenseCommand):void {
        _updateLicenseCommand = value;
    }

    public function get updateLicenseMediator():UpdateLicenseMediator {
        return _updateLicenseMediator;
    }

    public function set updateLicenseMediator(value:UpdateLicenseMediator):void {
        _updateLicenseMediator = value;
    }

    public function get getLicenseCommand():GetLicenseCommand {
        return _getLicenseCommand;
    }

    public function set getLicenseCommand(value:GetLicenseCommand):void {
        _getLicenseCommand = value;
    }

    public function dispose():void {
        // Clean up

        setViewComponent(null);
    }    
}
}
