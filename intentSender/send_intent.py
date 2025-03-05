from drozer.modules import common, Module
from drozer import android

class SendIntent(Module, common.PackageManager):
    
    def execute(self, arguments):
        intent = android.Intent(
            action="com.example.calculator.action.CALCULATE",  # Replace with your target action
            component=("com.example.calculator", "com.example.calculator.Calculator"),  # Replace with target app and activity
            flags=["ACTIVITY_NEW_TASK"]
        )

        # Add extras if needed
        intent.extras.append(("integer", "n1", "10"))
        intent.extras.append(("integer", "n2", "10"))
        intent.extras.append(("string", "op", "+"))

        self.getContext().startActivity(intent.buildIn(self))
        
# drozer console connect --command 'run app.activity.start --component com.example.victimapp/.SerialActivity --extra string key1 value1 --extra int key2 1234'
