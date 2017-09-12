/*
Knowage, Open Source Business Intelligence suite
Copyright (C) 2016 Engineering Ingegneria Informatica S.p.A.

Knowage is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

Knowage is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * @authors Giovanni Luca Ulivo (GiovanniLuca.Ulivo@eng.it)
 * v0.0.1
 * 
 */
(function() {
angular.module('cockpitModule')
.directive('cockpitStaticPivotTableWidget',function(cockpitModule_widgetServices){
	   return{
		   templateUrl: baseScriptPath+ '/directives/cockpit-widget/widget/staticPivotTableWidget/templates/staticPivotTableWidgetTemplate.html',
		   controller: cockpitStaticPivotTableWidgetControllerFunction,
		   compile: function (tElement, tAttrs, transclude) {
                return {
                    pre: function preLink(scope, element, attrs, ctrl, transclud) {
                    	element[0].classList.add("flex");
                    	element[0].classList.add("layout");
                    },
                    post: function postLink(scope, element, attrs, ctrl, transclud) {
                    	//init the widget
                    	element.ready(function () {
                    		scope.initWidget();
                    		});
                    	
                    	
                    	
                    }
                };
		   	}
	   }
}).run(function() { 
	//adds methods for IE11
	if (!String.prototype.startsWith) {
	    String.prototype.startsWith = function(searchString, position){
	      position = position || 0;
	      return this.substr(position, searchString.length) === searchString;
	  };
	}
	
	if (!String.prototype.endsWith) {
		  String.prototype.endsWith = function(searchString, position) {
		      var subjectString = this.toString();
		      if (typeof position !== 'number' || !isFinite(position) || Math.floor(position) !== position || position > subjectString.length) {
		        position = subjectString.length;
		      }
		      position -= searchString.length;
		      var lastIndex = subjectString.lastIndexOf(searchString, position);
		      return lastIndex !== -1 && lastIndex === position;
		  };
		}

});

function cockpitStaticPivotTableWidgetControllerFunction($scope,cockpitModule_widgetConfigurator,$q,$mdPanel,sbiModule_restServices,$compile,cockpitModule_generalOptions,$mdDialog,sbiModule_device){
	$scope.bordersSize=[{
    	label:$scope.translate.load("sbi.cockpit.style.borders.solid"),
    	value:'solid',
    	exampleClass:"borderExampleSolid"
    },
    {
    	label:$scope.translate.load("sbi.cockpit.style.borders.dashed"),
    	value:'dashed',
    	exampleClass:"borderExampleDashed"
    },
    {
    	label:$scope.translate.load("sbi.cockpit.style.borders.dotted"),
    	value:'dotted',
    	exampleClass:"borderExampleDotted"
    }
	];
	$scope.bordersWidth=[{
	    	label:$scope.translate.load("sbi.cockpit.style.small"),
	    	value:"0.1em"
	    },
	    {
	    	label:$scope.translate.load("sbi.cockpit.style.medium"),
	    	value:"0.3em"
	    },
	    {
	    	label:$scope.translate.load("sbi.cockpit.style.large"),
	    	value:"0.7em"
	    },
	    {
	    	label:$scope.translate.load("sbi.cockpit.style.extralarge"),
	    	value:"1em"
	    },
	];
	

	$scope.init=function(element,width,height){
		$scope.refreshWidget();
	};
	
	$scope.cleanProperties = function(config, obj, admitObject) {
		var toReturn = {};
		for (var c in config){
			if (c == obj){
				if (typeof config[c] == 'object'){
					var objProp = config[c];
					var propToReturn = {};
					for (p in objProp){							
						if (!admitObject && p.startsWith("{\"")){
							continue;	//skip the object element. ONLY attribute are added
						}
						propToReturn[p] = objProp[p];
					}	
					toReturn[c] = propToReturn;
				}						
			}else
				toReturn[c] = config[c];
		}
		return toReturn;
	}
	
	$scope.cleanObjectConfiguration = function(config, obj, admitObject){	
		
		if (Array.isArray(config)){
			var toReturnArray = [];
			for (var e=0; e<config.length; e++){
				var elem = config[e];
				toReturnArray.push($scope.cleanProperties(elem, obj, admitObject));				
			}
			return toReturnArray;
		}else{	
			var toReturn = {};
			toReturn = $scope.cleanProperties(config, obj, admitObject);
			return toReturn;
		}	
	}
	
	$scope.refresh=function(element,width,height, datasetRecords,nature){
		if(datasetRecords==undefined){
			return;
		}
		
		if(nature == 'resize' || nature == 'gridster-resized' || nature == 'fullExpand'){
			return;
		}
		$scope.showWidgetSpinner();
		
		var dataToSend={
				 config: {
				        type: "pivot"
				    },
				metadata: datasetRecords.metaData,
				jsonData: datasetRecords.rows,
				sortOptions:{}
		};
		
		angular.merge(dataToSend,$scope.ngModel.content);
		
		if( dataToSend.crosstabDefinition==undefined || 
			dataToSend.crosstabDefinition.measures==undefined||dataToSend.crosstabDefinition.measures.length==0 ||
			((dataToSend.crosstabDefinition.rows==undefined||dataToSend.crosstabDefinition.rows.length==0) &&
			(dataToSend.crosstabDefinition.columns==undefined||dataToSend.crosstabDefinition.columns.length==0)) ){
			console.log("crossTab non configured")
			$scope.hideWidgetSpinner();
			return;
		}
		
//		dataToSend.crosstabDefinition = $scope.cleanObjectConfiguration(dataToSend.crosstabDefinition, 'style', false);
		dataToSend.crosstabDefinition.measures = $scope.cleanObjectConfiguration(dataToSend.crosstabDefinition.measures, 'style', false);
		dataToSend.crosstabDefinition.rows = $scope.cleanObjectConfiguration(dataToSend.crosstabDefinition.rows, 'style', false);
		dataToSend.crosstabDefinition.columns = $scope.cleanObjectConfiguration(dataToSend.crosstabDefinition.columns, 'style', false);
		
		sbiModule_restServices.promisePost("1.0/crosstab","update",dataToSend).then(
				function(response){
					$scope.subCockpitWidget.html(response.data.htmlTable);
					$compile(angular.element($scope.subCockpitWidget).contents())($scope)
					$scope.addPivotTableStyle();
					$scope.hideWidgetSpinner();
				},
				function(response){
					sbiModule_restServices.errorHandler(response.data,"Pivot Table Error")
					$scope.hideWidgetSpinner();
					}
				)
	}
	
	
	$scope.selectRow=function(columnName,columnValue){
		$scope.doSelection(columnName,columnValue);
	};

	$scope.selectMeasure=function(rowHeaders, rowsValues, columnsHeaders, columnValues){
		var lstHeaders = []; //list of all headers (columns and rows)
		var lstValues = []; //list of all values (columns and rows)
		if (rowHeaders != ""){
			//adds all selection references about the row side
			var rowsHeads = rowHeaders.split("_S_");
			var rowsVals = rowsValues.split("_S_");
			for (var c=0; c < rowsHeads.length; c++){
				if (rowsHeads[c] == "") continue;
				var columnName = rowsHeads[c];
				var columnValue = rowsVals[c];
				lstHeaders.push(columnName);
				lstValues.push(columnValue);
			}
		}
		
		if (columnsHeaders != ""){
			//adds all selection references about the column side
			var columnHeads = columnsHeaders.split("_S_");
			var columnVals = columnValues.split("_S_");
			for (var c=0; c < columnHeads.length; c++){
				if (columnHeads[c] == "") continue;
				var columnName = columnHeads[c];
				var columnValue = columnVals[c];
				lstHeaders.push(columnName);
				lstValues.push(columnValue);
			}			
		}
		$scope.doSelection(lstHeaders,lstValues); //call selection method passing all headers and values (unique time)
		
	};

	
	$scope.enableAlternate = function(){
		$scope.colorPickerProperty['disabled'] = $scope.ngModel.content.style.showAlternateRows;
	}
	
	$scope.enableGrid =  function(){
		$scope.colorPickerPropertyGrid['disabled'] = $scope.ngModel.content.style.showGrid; 
	}
	
	
	
	$scope.addPivotTableStyle=function(){
		if($scope.ngModel.content.style!=undefined){
			var totalsItem;
			var subtotalsItem;
			var dataItem;
			var memberItem;
			var crossItem;
			//generic
			if($scope.ngModel.content.style.generic!=undefined && Object.keys($scope.ngModel.content.style.generic).length>0 ){
				totalsItem=angular.element($scope.subCockpitWidget[0].querySelectorAll(".totals"));
				subtotalsItem=angular.element($scope.subCockpitWidget[0].querySelectorAll(".partialsum"));
				dataItem=angular.element($scope.subCockpitWidget[0].querySelectorAll(".data"));
				memberItem=angular.element($scope.subCockpitWidget[0].querySelectorAll(".member"));
				crossItem=angular.element($scope.subCockpitWidget[0].querySelectorAll(".crosstab-header-text"));
				for(var prop in $scope.ngModel.content.style.generic){
					if ($scope.ngModel.content.style.generic[prop]!=""){
						totalsItem.css(prop,$scope.ngModel.content.style.generic[prop]);
						subtotalsItem.css(prop,$scope.ngModel.content.style.generic[prop]);
						dataItem.css(prop,$scope.ngModel.content.style.generic[prop]);
						memberItem.css(prop,$scope.ngModel.content.style.generic[prop]);
						crossItem.css(prop,$scope.ngModel.content.style.generic[prop]);
					}
				}
			}
			
			//altrnateRow & grid border
			if($scope.ngModel.content.style.measuresRow!=undefined && Object.keys($scope.ngModel.content.style.measuresRow).length>0 ){
				var rowList=angular.element($scope.subCockpitWidget[0].querySelectorAll("tr"));
				var tmpOddRow=false;
				angular.forEach(rowList,function(row,index){
					//apply borders on member class
					var dataColumnList=row.querySelectorAll(".member");
					if(dataColumnList.length>0){							
						$scope.applyBorderStyle(dataColumnList);								
					}
					dataColumnList=row.querySelectorAll(".memberNoStandardStyle");
					if(dataColumnList.length>0){							
						$scope.applyBorderStyle(dataColumnList);								
					}
					//apply borders on level class
					dataColumnList=row.querySelectorAll(".level");
					if(dataColumnList.length>0){							
						$scope.applyBorderStyle(dataColumnList);								
					}
					//apply borders on 'na' class
					dataColumnList=row.querySelectorAll(".na");
					if(dataColumnList.length>0){							
						$scope.applyBorderStyle(dataColumnList);								
					}
					//apply borders on 'na' class
					dataColumnList=row.querySelectorAll(".naNoStandardStyle");
					if(dataColumnList.length>0){							
						$scope.applyBorderStyle(dataColumnList);								
					}
					//apply borders on 'total' class
					dataColumnList=row.querySelectorAll(".totals");
					if(dataColumnList.length>0){							
						$scope.applyBorderStyle(dataColumnList);								
					}
					//apply borders on 'subtotal' class
					dataColumnList=row.querySelectorAll(".subTotals");
					if(dataColumnList.length>0){							
						$scope.applyBorderStyle(dataColumnList);								
					}
					//apply styles on data (values)
					dataColumnList=row.querySelectorAll(".data");
					if(dataColumnList.length == 0){
						dataColumnList=row.querySelectorAll(".dataNoStandardStyle"); //personal user settings						
					}
					if(dataColumnList.length>0){
						//alternateRow
						if(tmpOddRow && $scope.ngModel.content.style.measuresRow["odd-background-color"]!= ""){
							angular.element(dataColumnList).css("background-color",$scope.ngModel.content.style.measuresRow["odd-background-color"])
						}else if ($scope.ngModel.content.style.measuresRow["even-background-color"]!= ""){
							angular.element(dataColumnList).css("background-color",$scope.ngModel.content.style.measuresRow["even-background-color"])
						}
						tmpOddRow=!tmpOddRow;
						
						//border cell style
						$scope.applyBorderStyle(dataColumnList);		
					}else{
						tmpOddRow=false;
					}
				});
			}
			
			//totals
			if($scope.ngModel.content.style.totals!=undefined && Object.keys($scope.ngModel.content.style.totals).length>0 ){
				if(totalsItem==undefined){
					totalsItem=angular.element($scope.subCockpitWidget[0].querySelectorAll(".totals"));
				}
				for(var prop in $scope.ngModel.content.style.totals){
					if ($scope.ngModel.content.style.totals[prop]!= "")
						totalsItem.css(prop,$scope.ngModel.content.style.totals[prop])
				}
			}
			//subTotals
			if($scope.ngModel.content.style.subTotals!=undefined && Object.keys($scope.ngModel.content.style.subTotals).length>0 ){
				if(subtotalsItem==undefined){
					subtotalsItem=angular.element($scope.subCockpitWidget[0].querySelectorAll(".partialsum"));
				}
				for(var prop in $scope.ngModel.content.style.subTotals){
					if ($scope.ngModel.content.style.subTotals[prop] != "")
						subtotalsItem.css(prop,$scope.ngModel.content.style.subTotals[prop])
				}
			}
			
			//measures
			if($scope.ngModel.content.style.measures!=undefined && Object.keys($scope.ngModel.content.style.measures).length>0 ){
				if(dataItem==undefined){
					dataItem=angular.element($scope.subCockpitWidget[0].querySelectorAll(".data"));
				}
				for(var prop in $scope.ngModel.content.style.measures){
					if ($scope.ngModel.content.style.measures[prop] != "")
						dataItem.css(prop,$scope.ngModel.content.style.measures[prop])
				}
			}
			
			//measuresHeaders
			if($scope.ngModel.content.style.measuresHeaders!=undefined && Object.keys($scope.ngModel.content.style.measuresHeaders).length>0 ){
				if(memberItem==undefined){
					memberItem=angular.element($scope.subCockpitWidget[0].querySelectorAll(".member"));
				}
				for(var prop in $scope.ngModel.content.style.measuresHeaders){
					if ($scope.ngModel.content.style.measuresHeaders[prop]!= "")
						memberItem.css(prop,$scope.ngModel.content.style.measuresHeaders[prop])
				}
			}
			
			//crossTabHeaders
			if($scope.ngModel.content.style.crossTabHeaders!=undefined && Object.keys($scope.ngModel.content.style.crossTabHeaders).length>0 ){
				if(crossItem==undefined){
					crossItem=angular.element($scope.subCockpitWidget[0].querySelectorAll(".crosstab-header-text"));
				}
				for(var prop in $scope.ngModel.content.style.crossTabHeaders){
					if(angular.equals("background-color",prop) && $scope.ngModel.content.style.crossTabHeaders[prop]!= ""){
						crossItem.parent().parent().parent().parent().css(prop,$scope.ngModel.content.style.crossTabHeaders[prop])
					}else if ($scope.ngModel.content.style.crossTabHeaders[prop]!=""){
						crossItem.css(prop,$scope.ngModel.content.style.crossTabHeaders[prop])
					}
				}
			}
				
		}
		
	};
	
	//border cell style
	$scope.applyBorderStyle = function(dataColumnList){
		if ($scope.ngModel.content.style.showGrid){
			if($scope.ngModel.content.style.measuresRow["border-width"]!= ""){
				angular.element(dataColumnList).css("border-width",$scope.ngModel.content.style.measuresRow["border-width"])
			}
			if ($scope.ngModel.content.style.measuresRow["border-color"]!= ""){
				angular.element(dataColumnList).css("border-color",$scope.ngModel.content.style.measuresRow["border-color"])
			}
			if ($scope.ngModel.content.style.measuresRow["border-style"]!= ""){
				angular.element(dataColumnList).css("border-style",$scope.ngModel.content.style.measuresRow["border-style"])
			}
		}
	}
	
	$scope.editWidget=function(index){
		
		var finishEdit=$q.defer();
		var config = {
				attachTo:  angular.element(document.body),
				controller: function($scope,finishEdit,sbiModule_translate,model,mdPanelRef,cockpitModule_datasetServices,cockpitModule_generalOptions,$mdDialog,$mdToast,sbiModule_device){
			    	  $scope.translate=sbiModule_translate;
			    	  $scope.sbiModule_device=sbiModule_device;
			    	  $scope.localModel={};
			    	  $scope.currentDataset={};
			    	  $scope.originalCurrentDataset={};
			    	  $scope.dragUtils={dragObjectType:undefined};
			    	  $scope.tabsUtils={selectedIndex:0};
			    	  $scope.colorPickerProperty={placeholder:sbiModule_translate.load('sbi.cockpit.color.select') ,format:'rgb'}
			    	  $scope.cockpitModule_generalOptions=cockpitModule_generalOptions;
			    	  angular.copy(model,$scope.localModel);
			    	  
			    	  if($scope.localModel.content==undefined){
		    			  $scope.localModel.content={};
		    		  }
			    	  if($scope.localModel.content.crosstabDefinition==undefined){
		    			  $scope.localModel.content.crosstabDefinition={};
		    		  }
			    	  if($scope.localModel.content.crosstabDefinition.measures==undefined){
		    			  $scope.localModel.content.crosstabDefinition.measures=[];
		    		  }
			    	  if($scope.localModel.content.crosstabDefinition.rows==undefined){
			    		  $scope.localModel.content.crosstabDefinition.rows=[];
			    	  }
			    	  if($scope.localModel.content.crosstabDefinition.columns==undefined){
			    		  $scope.localModel.content.crosstabDefinition.columns=[];
			    	  }
			    	  if($scope.localModel.content.crosstabDefinition.config==undefined){
			    		  $scope.localModel.content.crosstabDefinition.config={};
			    	  }
			    	  if($scope.localModel.content.crosstabDefinition.config.measureson==undefined){
			    		  $scope.localModel.content.crosstabDefinition.config.measureson="columns";
			    	  }
			    	  if($scope.localModel.content.crosstabDefinition.config.percenton==undefined){
			    		  $scope.localModel.content.crosstabDefinition.config.percenton="no";
			    	  }
			    	  
			    	  
			    	  $scope.changeDatasetFunction=function(dsId,noReset){
			    		  $scope.currentDataset= cockpitModule_datasetServices.getDatasetById( dsId);
			    		  $scope.originalCurrentDataset=angular.copy( $scope.currentDataset);
			    		  if(noReset!=true){
			    			  $scope.localModel.content.crosstabDefinition.measures=[];
			    			  $scope.localModel.content.crosstabDefinition.rows=[];
			    			  $scope.localModel.content.crosstabDefinition.columns=[];
			    		  }
			    	  }
			    	  
			    	  if($scope.localModel.dataset!=undefined && $scope.localModel.dataset.dsId!=undefined){
			    		  $scope.changeDatasetFunction($scope.localModel.dataset.dsId,true)
			    	  }
			    	  
			    	  //remove used measure and attribute
			    	 $scope.clearUsedMeasureAndAttribute=function(){
			    		 if($scope.currentDataset.metadata==undefined){
			    			 return;
			    		 }
			    		 
			    		 var arrObje=["measures","rows","columns"];
			    		 var present=[];
			    		 for(var meas=0;meas<arrObje.length;meas++){
			    			 for(var i=0;i<$scope.localModel.content.crosstabDefinition[arrObje[meas]].length;i++){
			    				 present.push($scope.localModel.content.crosstabDefinition[arrObje[meas]][i].id);
			    			 }
			    		 }
			    		 
			    		 for(var i=0;i<$scope.currentDataset.metadata.fieldsMeta.length;i++){
			    			 if(present.indexOf($scope.currentDataset.metadata.fieldsMeta[i].name)!=-1){
			    				 $scope.currentDataset.metadata.fieldsMeta.splice(i,1);
			    				 i--;
			    			 }
			    		 }
			    		 
			    	 }
			    	 $scope.clearUsedMeasureAndAttribute();
			    	 
			    	 $scope.dropCallback=function(event, index, list,item, external, type, containerType){

			    		  if(angular.equals(type,containerType)){
			    			  var eleIndex=-1;
			    			  angular.forEach(list,function(ele,ind){
			    				  var tmp=angular.copy(ele);
			    				  delete tmp.$$hashKey;
			    				  if(angular.equals(tmp,item)){
			    					  eleIndex=ind;
			    				  }
			    			  });
			    			  
			    			  list.splice(eleIndex,1)
			    			  list.splice(index,0,item)
			    			  return false
			    		  }else{
			    			  var tmpItem;
			    			  if(angular.equals(containerType,"MEASURE-PT") || angular.equals(containerType,"COLUMNS") || angular.equals(containerType,"ROWS")){
			    				  
			    				  if( (angular.equals(containerType,"COLUMNS") &&  angular.equals(type,"ROWS")) || (angular.equals(containerType,"ROWS") &&  angular.equals(type,"COLUMNS"))){
			    					  tmpItem=item;
			    				  }else{
			    					  //convert item in specific format 
			    					   tmpItem={
			    							  id: item.name,
			    							  alias: item.alias,
			    							  containerType : containerType,
			    							  iconCls: item.fieldType.toLowerCase(),			    							  
			    							  nature: item.fieldType.toLowerCase(),
			    							  values: "[]",
			    							  sortable: false,
			    							  width: 0
			    					  };
			    					   if(angular.equals(containerType,"MEASURE-PT")){
			    						   tmpItem.funct="SUM";
			    					   }
			    							   
			    				  }
			    				  
			    				  
			    			  }else{
			    				  //containerType == MEASURE or ATTRIBUTE
			    				  //load element from dataset field
			    				  for(var i=0;i<$scope.originalCurrentDataset.metadata.fieldsMeta.length;i++){
			    					  if(angular.equals($scope.originalCurrentDataset.metadata.fieldsMeta[i].name,item.id)){
			    						  tmpItem=angular.copy($scope.originalCurrentDataset.metadata.fieldsMeta[i]);
			    						  break;
			    					  }
			    				  }
			    			  }
			    		  
			    		  
			    			  list.splice(index,0,tmpItem)
			    			  return true;
			    		  }
			    		  
			    	  }
			    	  
			     
			    	  $scope.saveConfiguration=function(){
			    		  if($scope.localModel.dataset == undefined){
			  				$scope.showAction($scope.translate.load('sbi.cockpit.table.missingdataset'));
			    			return;
			    		  }
			    		  if($scope.localModel.content.crosstabDefinition.measures.length == 0 ||
			    			($scope.localModel.content.crosstabDefinition.rows.length == 0 &&
			    			$scope.localModel.content.crosstabDefinition.columns.length ==0)
			    		  ){
			    			  $scope.showAction($scope.translate.load('sbi.cockpit.widgets.staticpivot.missingfield'));
			    			  return;
			    		  }
			    		  angular.copy($scope.localModel,model);
			    		  mdPanelRef.close();
			    		  $scope.$destroy();
			    		  finishEdit.resolve();

			    	  }
			    	  
			  		$scope.showAction = function(text) {
						var toast = $mdToast.simple()
						.content(text)
						.action('OK')
						.highlightAction(false)
						.hideDelay(3000)
						.position('top')

						$mdToast.show(toast).then(function(response) {
							if ( response == 'ok' ) {
							}
						});
					}
			    	  $scope.cancelConfiguration=function(){
			    		  mdPanelRef.close();
			    		  $scope.$destroy();
			    		  finishEdit.reject();

			    	  }
			    	  
			    	  $scope.editFieldsProperty=function(selectedColumn){			    	
			    		  $mdDialog.show({
								templateUrl:  baseScriptPath+ '/directives/cockpit-columns-configurator/templates/cockpitColumnStyle.html',
								parent : angular.element(document.body),
								clickOutsideToClose:true,
								escapeToClose :true,
								preserveScope: true,
								autoWrap:false,
								fullscreen: true,
								locals:{model:$scope.localModel, selectedColumn:selectedColumn},
								controller: cockpitStyleColumnFunction

							}).then(function(answer) { 			
								console.log("Selected column:", $scope.selectedColumn);

							}, function() {
								console.log("Selected column:", $scope.selectedColumn);
							});
			    	  }
			      },
				disableParentScroll: true,
				templateUrl: baseScriptPath+ '/directives/cockpit-widget/widget/staticPivotTableWidget/templates/staticPivotTableWidgetEditPropertyTemplate.html',
				position: $mdPanel.newPanelPosition().absolute().center(),
				fullscreen :true,
				hasBackdrop: true,
				clickOutsideToClose: false,
				escapeToClose: false,
				focusOnOpen: true,
				preserveScope: true,
				locals: {finishEdit:finishEdit,model:$scope.ngModel},
				onRemoving :function(){
					$scope.refreshWidget();
				}
		};

		$mdPanel.open(config);
		return finishEdit.promise;
		
	}
	

	function cockpitStyleColumnFunction($scope,sbiModule_translate,$mdDialog,model,selectedColumn,cockpitModule_datasetServices,cockpitModule_generalOptions,$mdToast){
		$scope.translate=sbiModule_translate;
		$scope.selectedColumn = angular.copy(selectedColumn);
		$scope.selectedColumn.fieldType = selectedColumn.nature.toUpperCase();
		$scope.selectedColumn.widgetType = "staticPivotTable";		
		$scope.selectedColumn.showHeader = (selectedColumn.showHeader==undefined)?true:selectedColumn.showHeader;
		$scope.AggregationFunctions= cockpitModule_generalOptions.aggregationFunctions;
		$scope.fontWeight = ['','normal','bold','bolder','lighter','number','initial','inherit'];
		$scope.textAlign = ['','left','right','center'];
		$scope.formatPattern = ['','#.###','#,###','#.###,##','#,###.##'];
		$scope.colorPickerProperty={placeholder:sbiModule_translate.load('sbi.cockpit.color.select') ,format:'rgb'}
		$scope.visTypes=['Text','Icon only'];
		$scope.selectedColumn.disableShowHeader = false; //default is enabled: only for measures force disable if there are many measures
		if ($scope.selectedColumn.containerType && $scope.selectedColumn.containerType == 'MEASURE-PT'){
			if (model.content.crosstabDefinition.measures.length==1)
				$scope.selectedColumn.disableShowHeader  = false;
			else{
				$scope.selectedColumn.disableShowHeader  = true;
				$scope.selectedColumn.showHeader = true;
			}
		}
		
		if(!$scope.selectedColumn.hasOwnProperty('colorThresholdOptions'))
		{	
			$scope.selectedColumn.colorThresholdOptions={};
			$scope.selectedColumn.colorThresholdOptions.condition=[];
			for(var i=0;i<3;i++)
			{
				$scope.selectedColumn.colorThresholdOptions.condition[i]="none";
			}
		}	
		
		
		if($scope.selectedColumn.visType==undefined)
		{
			$scope.selectedColumn.visType="Text";
		}	
		if($scope.selectedColumn.minValue==undefined||$scope.selectedColumn.minValue===''||$scope.selectedColumn.maxValue==undefined||$scope.selectedColumn.maxValue==='')
		{
			$scope.selectedColumn.minValue=0;
			$scope.selectedColumn.maxValue=100;
		}	
		if($scope.selectedColumn.chartColor==undefined||$scope.selectedColumn.chartColor==='')
		{	
			$scope.selectedColumn.chartColor="rgb(19, 30, 137)";
		}
		if($scope.selectedColumn.chartLength==undefined||$scope.selectedColumn.chartLength==='')
		{
			$scope.selectedColumn.chartLength=200;
		}

	                        
		$scope.conditions=['none','>','<','=','>=','<=','!='];
		if($scope.selectedColumn.scopeFunc==undefined)
		{	
			$scope.selectedColumn.scopeFunc={conditions:$scope.conditions, condition:[{condition:'none'},{condition:'none'},{condition:'none'},{condition:'none'}]};  
		}
		//------------------------- Threshold icon table -----------------------------	
		var conditionString0="	<md-input-container class='md-block'> 	<md-select ng-model='scopeFunctions.condition[0].condition'>	<md-option ng-repeat='cond in scopeFunctions.conditions' value='{{cond}}'>{{cond}}</md-option>	</md-select> </md-input-container>"
		var conditionString1="	<md-input-container class='md-block'> 	<md-select ng-model='scopeFunctions.condition[1].condition'>	<md-option ng-repeat='cond in scopeFunctions.conditions' value='{{cond}}'>{{cond}}</md-option>	</md-select> </md-input-container>"
		var conditionString2="	<md-input-container class='md-block'> 	<md-select ng-model='scopeFunctions.condition[2].condition'>	<md-option ng-repeat='cond in scopeFunctions.conditions' value='{{cond}}'>{{cond}}</md-option>	</md-select> </md-input-container>"
		var conditionString3="	<md-input-container class='md-block'> 	<md-select ng-model='scopeFunctions.condition[3].condition'>	<md-option ng-repeat='cond in scopeFunctions.conditions' value='{{cond}}'>{{cond}}</md-option>	</md-select> </md-input-container>"

			
		var valueString0="<md-input-container class='md-block' ng-if='scopeFunctions.condition[0].condition!=undefined && scopeFunctions.condition[0].condition!=\"none\"' flex>	<input class='input_class'  ng-model='scopeFunctions.condition[0].value' type='number' required> </md-input-container>";	
		var valueString1="<md-input-container class='md-block' ng-if='scopeFunctions.condition[1].condition!=undefined && scopeFunctions.condition[1].condition!=\"none\"' flex>	<input class='input_class'  ng-model='scopeFunctions.condition[1].value' type='number' required> </md-input-container>";	
		var valueString2="<md-input-container class='md-block' ng-if='scopeFunctions.condition[2].condition!=undefined && scopeFunctions.condition[2].condition!=\"none\"' flex>	<input class='input_class'  ng-model='scopeFunctions.condition[2].value' type='number' required> </md-input-container>";	
		var valueString3="<md-input-container class='md-block' ng-if='scopeFunctions.condition[3].condition!=undefined && scopeFunctions.condition[3].condition!=\"none\"' flex>	<input class='input_class'  ng-model='scopeFunctions.condition[3].value' type='number' required> </md-input-container>";	

		$scope.thresholdsList=
			[{priority:0, icon:"<md-icon style='color:red'  md-font-icon='fa fa-exclamation-circle' ng-init='scopeFunctions.condition[0].iconColor=\"red\";	scopeFunctions.condition[0].icon=\"fa fa-exclamation-circle\"'></md-icon>",condition:conditionString0,	value:valueString0},{priority:1 , icon:"<md-icon style='color:red'	md-font-icon='fa fa-times-circle' ng-init='scopeFunctions.condition[1].iconColor=\"red\"; scopeFunctions.condition[1].icon=\"fa fa-times-circle\"'></md-icon>",condition:conditionString1, value:valueString1},	{priority:2 , icon:"<md-icon style='color:yellow'  md-font-icon='fa fa-exclamation-triangle' ng-init='scopeFunctions.condition[2].iconColor=\"yellow\"; scopeFunctions.condition[2].icon=\"fa fa-exclamation-triangle\"'></md-icon>",condition:conditionString2, value:valueString2},{priority:3 , icon:"<md-icon style='color:green'  md-font-icon='fa fa-check-circle' ng-init='scopeFunctions.condition[3].iconColor=\"green\";	scopeFunctions.condition[3].icon=\"fa fa-check-circle\"'></md-icon>",condition:conditionString3, value:valueString3}];	
		$scope.tableColumns=[{label:"Icon",name:"icon", hideTooltip:true},{label:"Condition",name:"condition", hideTooltip:true},{label:"Value",name:"value", hideTooltip:true}];
		
		//----------------------- Cell color table ------------------------------------
		
		var condString0="	<md-input-container class='md-block'> 	<md-select ng-model='scopeFunctions.condition[0].condition'>	<md-option ng-repeat='cond in scopeFunctions.conditions' value='{{cond}}'>{{cond}}</md-option>	</md-select> </md-input-container>"
		var condString1="	<md-input-container class='md-block'> 	<md-select ng-model='scopeFunctions.condition[1].condition'>	<md-option ng-repeat='cond in scopeFunctions.conditions' value='{{cond}}'>{{cond}}</md-option>	</md-select> </md-input-container>"
		var condString2="	<md-input-container class='md-block'> 	<md-select ng-model='scopeFunctions.condition[2].condition'>	<md-option ng-repeat='cond in scopeFunctions.conditions' value='{{cond}}'>{{cond}}</md-option>	</md-select> </md-input-container>"

		var valString0="<md-input-container class='md-block' ng-if='scopeFunctions.condition[0].condition!=undefined && scopeFunctions.condition[0].condition!=\"none\"' flex>	<input class='input_class'  ng-model='scopeFunctions.condition[0].value' type='number' required> </md-input-container>";	
		var valString1="<md-input-container class='md-block' ng-if='scopeFunctions.condition[1].condition!=undefined && scopeFunctions.condition[1].condition!=\"none\"' flex>	<input class='input_class'  ng-model='scopeFunctions.condition[1].value' type='number' required> </md-input-container>";	
		var valString2="<md-input-container class='md-block' ng-if='scopeFunctions.condition[2].condition!=undefined && scopeFunctions.condition[2].condition!=\"none\"' flex>	<input class='input_class'  ng-model='scopeFunctions.condition[2].value' type='number' required> </md-input-container>";	

				
		$scope.cellColorThresholdsList=[{priority:0, color:"<md-input-container class=\"md-block\">  <color-picker  options=\"{format:'rgb'}\" ng-model=\"scopeFunctions.colorCondition[0].value \"></color-picker>  </md-input-container>",condition:condString0, value:valString0},{priority:1 , color:"<md-input-container class=\"md-block\"> <color-picker  options=\"{format:'rgb'}\" ng-model=\"scopeFunctions.colorCondition[1].value \"></color-picker></md-input-container>",condition:condString1, value:valString1},{priority:2 , color:"<md-input-container class=\"md-block\"> <color-picker  options=\"{format:'rgb'}\" ng-model=\"scopeFunctions.colorCondition[2].value \"></color-picker></md-input-container>",condition:condString2, value:valString2}];		
		$scope.cellColorTableColumns=[{label:"Color",name:"color", hideTooltip:true},{label:"Condition",name:"condition", hideTooltip:true},{label:"Value",name:"value", hideTooltip:true}];
		
		//----------------------------------------------------------------------------


		$scope.cleanStyleColumn = function(){
			$scope.selectedColumn.style = undefined;
		}
		$scope.saveColumnStyleConfiguration = function(){
//			selectedColumn = $scope.cleanObjectConfiguration(selectedColumn, 'style', false);
			$scope.selectedColumn = $scope.cleanObjectConfiguration($scope.selectedColumn, 'style', false);
			angular.copy($scope.selectedColumn,selectedColumn);

			$mdDialog.cancel();
		}

		$scope.cancelcolumnStyleConfiguration = function(){
			$mdDialog.cancel();
		}
		
		$scope.cleanObjectConfiguration = function(config, obj, admitObject){
			var toReturn = {};
			for (var c in config){
				if (c == obj){
					if (typeof config[c] == 'object'){
						var objProp = config[c];
						var propToReturn = {};
						for (p in objProp){							
							if (!admitObject && p.startsWith("{\"")){
								continue;	//skip the object element. ONLY attribute are added
							}
							propToReturn[p] = objProp[p];
						}	
						toReturn[c] = propToReturn;
					}						
				}else
					toReturn[c] = config[c];
			}
			return toReturn;
		}
		
		
		$scope.checkIfDisable = function(){
			
			if($scope.selectedColumn.selectThreshold==true)
			{	
				if($scope.selectedColumn.threshold==undefined||$scope.selectedColumn.threshold=="")
				{
					return true;
				}				
			}	
			
			if($scope.selectedColumn.maxValue==undefined || $scope.selectedColumn.minValue==undefined || $scope.selectedColumn.maxValue==="" || $scope.selectedColumn.minValue==="")
			{
				return true;
			}
			
			for(var i=0;i<$scope.selectedColumn.scopeFunc.condition.length;i++)
			{
				if($scope.selectedColumn.scopeFunc.condition[i].condition!=undefined && $scope.selectedColumn.scopeFunc.condition[i].condition!="none")
				{
					if($scope.selectedColumn.scopeFunc.condition[i].value==="" || $scope.selectedColumn.scopeFunc.condition[i].value==undefined)
					{
						return true;
					}	
				}	
			}
			return false;
		}
	}

	
	$scope.orderPivotTable=function(column, axis, globalId, measureLabel, parentValue){
		if($scope.ngModel.content.sortOptions==undefined){
			$scope.ngModel.content.sortOptions={};
		}
		var axisConfig;
		if(axis==1){
			if (measureLabel){
				var previousSelection = $scope.getPreviousSelection($scope.ngModel.content.sortOptions.measuresSortKeys);
				if($scope.ngModel.content.sortOptions.measuresSortKeys==undefined || previousSelection!=column){
					$scope.ngModel.content.sortOptions.measuresSortKeys={}
				}	
				$scope.ngModel.content.sortOptions.measuresSortKeys.parentValue = parentValue;
				$scope.ngModel.content.sortOptions.measuresSortKeys.measureLabel = measureLabel;
				axisConfig = $scope.ngModel.content.sortOptions.measuresSortKeys;
			}else{
				var previousSelection = $scope.getPreviousSelection($scope.ngModel.content.sortOptions.columnsSortKeys);
				if($scope.ngModel.content.sortOptions.columnsSortKeys==undefined || previousSelection!=column){
					$scope.ngModel.content.sortOptions.columnsSortKeys={}
				}
				//reset measure ordering
				if($scope.ngModel.content.sortOptions.measuresSortKeys!=undefined ){
					$scope.ngModel.content.sortOptions.measuresSortKeys=undefined;
				}
				axisConfig = $scope.ngModel.content.sortOptions.columnsSortKeys;
			}			
		}else{
			if (measureLabel){
				var previousSelection = $scope.getPreviousSelection($scope.ngModel.content.sortOptions.measuresSortKeys);
				if($scope.ngModel.content.sortOptions.measuresSortKeys==undefined || previousSelection!=column){
					$scope.ngModel.content.sortOptions.measuresSortKeys={}
				}				
				$scope.ngModel.content.sortOptions.measuresSortKeys.parentValue = parentValue;
				$scope.ngModel.content.sortOptions.measuresSortKeys.measureLabel = measureLabel;
				axisConfig = $scope.ngModel.content.sortOptions.measuresSortKeys;
			}else{
				var previousSelection = $scope.getPreviousSelection($scope.ngModel.content.sortOptions.rowsSortKeys);
				if($scope.ngModel.content.sortOptions.rowsSortKeys==undefined || previousSelection!=column){
					$scope.ngModel.content.sortOptions.rowsSortKeys={}
				}
				//reset measure ordering
				if($scope.ngModel.content.sortOptions.measuresSortKeys!=undefined ){
					$scope.ngModel.content.sortOptions.measuresSortKeys=undefined;
				}
				axisConfig = $scope.ngModel.content.sortOptions.rowsSortKeys;
			}
		}

		var direction = axisConfig[column];
		if(!direction){
			direction = 1;
		}
		direction = direction*(-1);

		axisConfig[column] = direction;
		
		$scope.refreshWidget();
	}
	
	$scope.getPreviousSelection = function (keys){
		var toReturn = undefined;
		
		if(keys!=undefined){
			for (var m in keys){
				toReturn = m;
				break;
			}
		}
			
		return toReturn;
	}
	
	
};


//this function register the widget in the cockpitModule_widgetConfigurator factory
addWidgetFunctionality("static-pivot-table",{'initialDimension':{'width':20, 'height':20},'updateble':true,'cliccable':true});

})();