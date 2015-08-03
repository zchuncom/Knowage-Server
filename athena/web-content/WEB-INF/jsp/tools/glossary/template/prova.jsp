<md-dialog aria-label="New glossary" style="width: 80%;  overflow-y: visible;">
<md-toolbar>
    <div class="md-toolbar-tools">
      <h2 style="    font-size: 20px;    text-align: center;    width: 100%;">{{gloCtrl.headerTitle}}</h2>
       </div>
  </md-toolbar>
  <form name="glossaryForm" class="wordForm md-padding" novalidate    >
  <md-dialog-content class="sticky-container">
    

	<div layout="row" layout-wrap>
		<div flex="100">

			<md-input-container class="md-icon-float"> <!-- Use floating label instead of placeholder -->
			<label>{{translate.load("sbi.generic.name");}}</label> <md-icon md-font-icon="fa  fa-sitemap "	class="wo2" ></md-icon> 
			<input ng-model="gloCtrl.newGloss.GLOSSARY_NM" type="text" maxlength="100" > </md-input-container>
		</div>
	</div>
	
	<div layout="row" layout-wrap>
		<div flex="100">

			<md-input-container class="md-icon-float"> <!-- Use floating label instead of placeholder -->
			<label>{{translate.load("sbi.generic.code");}}</label> <md-icon md-font-icon="fa   fa-terminal "	class="wo3" ></md-icon> 
			<input ng-model="gloCtrl.newGloss.GLOSSARY_CD" type="text" maxlength="30" > </md-input-container>
		</div>
	</div>
	
	
	<div layout="row" layout-wrap>
		<div flex="100">
			<md-input-container class="md-icon-float" ng-class="{ 'md-input-hasnt-value' : gloCtrl.newGloss.GLOSSARY_DS.length === 0  }"> <!-- Use floating label instead of placeholder -->
			<label>{{translate.load("sbi.generic.descr");}}</label> 
			<md-icon md-font-icon="fa  fa-file-text-o "	class="formu" ></md-icon>	
				 <textarea ng-model="gloCtrl.newGloss.GLOSSARY_DS" columns="1" md-maxlength="500" maxlength="500"  ></textarea>
				 </md-input-container>
		</div>
	</div>
	

  </md-dialog-content>
     
  <div class="md-actions" layout="row">
   
    <md-button ng-click="gloCtrl.annulla()" class="md-primary">
  {{translate.load("sbi.ds.wizard.cancel");}} 
    </md-button>
  
    <md-button ng-click="gloCtrl.submit()" class="md-primary" ng-disabled="gloCtrl.newGloss.GLOSSARY_NM.length === 0 "  >
    {{translate.load("sbi.generic.update");}}
    </md-button>
  </div>
  
  
  </form>
</md-dialog>