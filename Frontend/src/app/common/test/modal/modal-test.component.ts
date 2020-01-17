import {Component} from '@angular/core';
import {Collection, DialogButton, RestCollectionService, RestConnectorService, RestConstants} from '../../../core-module/core.module';

@Component({
  selector: 'app-modal-test',
  styleUrls: ['./modal-test.component.scss'],
  templateUrl: './modal-test.component.html',
})
export class ModalTestComponent {
    contentLengths = Array(20).fill(0).map((x,i) => i);
    card = false;
    options: any= {
        title:'Title',
        subtitle:'Subtitle',
        width: 'normal',
        height: 'normal',
        isCancelable: true
    };
    sizes = ['auto', 'small', 'normal', 'large', 'xlarge', 'xxlarge'];
    buttons = [
        new DialogButton('Negative',DialogButton.TYPE_CANCEL,() => this.card = false),
        new DialogButton('Positive',DialogButton.TYPE_PRIMARY,() => this.card = false),
    ];
    public textSize = 3;

    getTextSize() {
        return Array(this.textSize);
    }
}
