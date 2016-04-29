(function() {

	var scripts = document.getElementsByTagName("script");
	var currentScriptPath = scripts[scripts.length - 1].src;
	currentScriptPath = currentScriptPath.substring(0, currentScriptPath.lastIndexOf('/') + 1);


	angular.module('kpi-widget', ['ngMaterial','sbiModule'])
	.directive('kpiWidget', function() {
		return {
			templateUrl: currentScriptPath + 'template/kpi-widget.jsp',
			controller: kpiWidgetController,
			scope: {
				//			ngModel: '=',
				gaugeSize:'=',
				minValue:'=',
				maxValue:'=',
				value:'=',
				targetValue:'=',
				thresholdStops:'=',
				precision:'=?',
				valueSeries: '=',

				widgetId:'=',
				label:'=',
				fontConf:'=',
				showTargetPercentage:'=',
				showThresholds: '=?',
				canSee:'='
			},

		};
	});

	function kpiWidgetController($scope,$mdDialog,$q,$mdToast,$timeout,sbiModule_restServices,sbiModule_translate,sbiModule_config){
		//	$scope.documentData = $scope.ngModel;
//		debugger;


		if($scope.precision){
			$scope.value = $scope.value.toFixed($scope.precision);
			$scope.targetValue = $scope.targetValue.toFixed($scope.precision);
		}


		$scope.options = {
				chart: {
					type: 'stackedAreaChart',
					height: 250,
					width:400,
					margin : {
						//  top: 20,
						right: 0,
						//   bottom: 30,
						left: 0
					},
					color:['#C4DCF3'],
					x: function(d){return d[0];},
					y: function(d){return d[1];},
					useVoronoi: false,
					clipEdge: true,
					duration: 100,
					style:{
						border:"black"
					},
					useInteractiveGuideline: true,
					xAxis: {
						showMaxMin: false,
						tickFormat: function(d) {
							return d3.time.format('%x')(new Date(d))
						}
					},
					yAxis: {
						tickFormat: function(d){
							return d3.format(',.2f')(d);
						}
					},

				}
		};




		$scope.$watch('valueSeries',function (newValue, oldValue) {
			if(newValue!=oldValue){
				var values = $scope.convertToStackedAreaChartData(newValue);
				$scope.data = [{"values" :	values}];
				console.log("Guarda:",values);
			}
		}
		, true);

		$scope.convertToStackedAreaChartData= function(arrKpi){
			var array = [];
			if(arrKpi!=undefined){
				for(var i=0;i<arrKpi.length;i++){
					var arrTemp = [];
					arrTemp.push(arrKpi[i].timeRun,arrKpi[i].computedValue);
					array.push(arrTemp);

				}
				return array;
			}
			return [];
		},

		$scope.data = [{
			"values" : [ [ 1025409600000 , 9.3433263069351] , [ 1028088000000 , 8.4583069475546] , [ 1030766400000 , 8.0342398154196] , [ 1033358400000 , 8.1538966876572] , [ 1036040400000 , 10.743604786849] , [ 1038632400000 , 12.349366155851] , [ 1041310800000 , 10.742682503899] , [ 1043989200000 , 11.360983869935] , [ 1046408400000 , 11.441336039535] , [ 1049086800000 , 10.897508791837] , [ 1051675200000 , 11.469101547709] , [ 1054353600000 , 12.086311476742] , [ 1056945600000 , 8.0697180773504] , [ 1059624000000 , 8.2004392233445] , [ 1062302400000 , 8.4566434900643] , [ 1064894400000 , 7.9565760979059] , [ 1067576400000 , 9.3764619255827] , [ 1070168400000 , 9.0747664160538] , [ 1072846800000 , 10.508939004673] , [ 1075525200000 , 10.69936754483] , [ 1078030800000 , 10.681562399145] , [ 1080709200000 , 13.184786109406] , [ 1083297600000 , 12.668213052351] , [ 1085976000000 , 13.430509403986] , [ 1088568000000 , 12.393086349213] , [ 1091246400000 , 11.942374044842] , [ 1093924800000 , 12.062227685742] , [ 1096516800000 , 11.969974363623] , [ 1099195200000 , 12.14374574055] , [ 1101790800000 , 12.69422821995] , [ 1104469200000 , 9.1235211044692] , [ 1107147600000 , 8.758211757584] , [ 1109566800000 , 8.8072309258443] , [ 1112245200000 , 11.687595946835] , [ 1114833600000 , 11.079723082664] , [ 1117512000000 , 12.049712896076] , [ 1120104000000 , 10.725319428684] , [ 1122782400000 , 10.844849996286] , [ 1125460800000 , 10.833535488461] , [ 1128052800000 , 17.180932407865] , [ 1130734800000 , 15.894764896516] , [ 1133326800000 , 16.412751299498] , [ 1136005200000 , 12.573569093402] , [ 1138683600000 , 13.242301508051] , [ 1141102800000 , 12.863536342041] , [ 1143781200000 , 21.034044171629] , [ 1146369600000 , 21.419084618802] , [ 1149048000000 , 21.142678863692] , [ 1151640000000 , 26.56848967753] , [ 1154318400000 , 24.839144939906] , [ 1156996800000 , 25.456187462166] , [ 1159588800000 , 26.350164502825] , [ 1162270800000 , 26.478333205189] , [ 1164862800000 , 26.425979547846] , [ 1167541200000 , 28.191461582256] , [ 1170219600000 , 28.930307448808] , [ 1172638800000 , 29.521413891117] , [ 1175313600000 , 28.188285966466] , [ 1177905600000 , 27.704619625831] , [ 1180584000000 , 27.49086242483] , [ 1183176000000 , 28.770679721286] , [ 1185854400000 , 29.06048067145] , [ 1188532800000 , 28.240998844973] , [ 1191124800000 , 33.004893194128] , [ 1193803200000 , 34.075180359928] , [ 1196398800000 , 32.548560664834] , [ 1199077200000 , 30.629727432729] , [ 1201755600000 , 28.642858788159] , [ 1204261200000 , 27.973575227843] , [ 1206936000000 , 27.393351882726] , [ 1209528000000 , 28.476095288522] , [ 1212206400000 , 29.29667866426] , [ 1214798400000 , 29.222333802896] , [ 1217476800000 , 28.092966093842] , [ 1220155200000 , 28.107159262922] , [ 1222747200000 , 25.482974832099] , [ 1225425600000 , 21.208115993834] , [ 1228021200000 , 20.295043095268] , [ 1230699600000 , 15.925754618402] , [ 1233378000000 , 17.162864628346] , [ 1235797200000 , 17.084345773174] , [ 1238472000000 , 22.24600710228] , [ 1241064000000 , 24.530543998508] , [ 1243742400000 , 25.084184918241] , [ 1246334400000 , 16.606166527359] , [ 1249012800000 , 17.239620011628] , [ 1251691200000 , 17.336739127379] , [ 1254283200000 , 25.478492475754] , [ 1256961600000 , 23.017152085244] , [ 1259557200000 , 25.617745423684] , [ 1262235600000 , 24.061133998641] , [ 1264914000000 , 23.223933318646] , [ 1267333200000 , 24.425887263936] , [ 1270008000000 , 35.501471156693] , [ 1272600000000 , 33.775013878675] , [ 1275278400000 , 30.417993630285] , [ 1277870400000 , 30.023598978467] , [ 1280548800000 , 33.327519522436] , [ 1283227200000 , 31.963388450372] , [ 1285819200000 , 30.49896723209] , [ 1288497600000 , 32.403696817913] , [ 1291093200000 , 31.47736071922] , [ 1293771600000 , 31.53259666241] , [ 1296450000000 , 41.760282761548] , [ 1298869200000 , 45.605771243237] , [ 1301544000000 , 39.986557966215] , [ 1304136000000 , 43.84633051005] , [ 1306814400000 , 39.857316881858] , [ 1309406400000 , 37.675127768207] , [ 1312084800000 , 35.775077970313] , [ 1314763200000 , 48.631009702578] , [ 1317355200000 , 42.830831754505] , [ 1320033600000 , 35.611502589362] , [ 1322629200000 , 35.320136981738] , [ 1325307600000 , 31.564136901516] , [ 1327986000000 , 32.074407502433] , [ 1330491600000 , 35.053013769977] , [ 1333166400000 , 33.873085184128] , [ 1335758400000 , 32.321039427046]]
		}];

		$scope.getValueToShow = function(){

			if($scope.value>=1000){
				return ((Number($scope.value)/1000).toFixed($scope.precision))+"K";

			}else{
				return Number($scope.value).toFixed($scope.precision);
			}
		}
		$scope.getTargetToShow = function(){
			if($scope.targetValue>=1000){
				return (Number($scope.targetValue)/1000).toFixed($scope.precision)+"K";

			}else{
				return $scope.targetValue;
			}
		}

		$scope.getPercentage = function(){
			if($scope.targetValue!=0){
				return (($scope.value / $scope.targetValue)*100).toFixed($scope.precision);
			}else{
				return 0;
			}
		}


		$scope.openEdit = function(){
			var deferred = $q.defer();
			$mdDialog.show({
				controller: DialogController,
				templateUrl: '/knowagekpiengine/js/angular_1.x/kpi-widget/template/kpi-widget-editValue.jsp',
				clickOutsideToClose:true,
				preserveScope:true,
				locals: {items: deferred,label:$scope.label,value:$scope.value, targetValue:$scope.targetValue,valueSeries:$scope.valueSeries[$scope.valueSeries.length-1] }
			})
			.then(function(answer) {
				$scope.status = 'You said the information was "' + answer + '".';
				return deferred.resolve($scope.selectedFunctionalities);
			}, function() {
				$scope.status = 'You cancelled the dialog.';
				if(deferred.promise.$$state.value!=undefined){
					$scope.value = deferred.promise.$$state.value.value;
				}
				if(deferred.promise.$$state.comment!=undefined){
					$scope.valueSeries[$scope.valueSeries.length-1].manualNote = deferred.promise.$$state.value.comment;
				}
				$scope.getValueToShow();
			});
			return deferred.promise;
		}
	};

	function DialogController($scope,$mdDialog,sbiModule_restServices,sbiModule_config,items,label,value,targetValue,valueSeries){
		$scope.label = label;
		$scope.value = value;
		$scope.targetValue =targetValue;
		$scope.valueSeries = valueSeries;
		$scope.array = [];
		
		$scope.parseLogicalKey = function(){
			var string  = $scope.valueSeries.logicalKey;
			var char = string.split(",");
			$scope.array = [];
			for(var i=0;i<char.length;i++){
				var values = char[i].split("=")
				var obj = {};
				obj["label"] = values[0];
				obj["value"] = values[1];
				$scope.array.push(obj);
			}
		}
		$scope.parseLogicalKey();
		$scope.close = function(){
			$mdDialog.cancel();

		}
		$scope.apply = function(){
			$mdDialog.cancel();
			$scope.kpiValueToSave = {};
			$scope.kpiValueToSave["manualValue"] = $scope.value;
			$scope.kpiValueToSave["manualNote"] = $scope.valueSeries.comment;
			$scope.kpiValueToSave["valueSeries"] = $scope.valueSeries;
			sbiModule_restServices.alterContextPath( sbiModule_config.externalBasePath );
			sbiModule_restServices.promisePost("1.0/kpi", 'editKpiValue',$scope.kpiValueToSave)
			
			.then(function(response){ 
				var obj = {};
				obj["value"] = $scope.value;
				obj["comment"] = $scope.kpiValueToSave["manualNote"];
				items.resolve(obj);
				console.log("Saved");
			},function(response){
				$scope.errorHandler(response.data,"");
			});

		}

		

	}


})();