import { animate, group, keyframes, state, style, transition, trigger } from '@angular/animations';
import {
    AfterContentInit,
    ChangeDetectorRef,
    Component,
    ContentChild,
    Directive,
    ElementRef, EventEmitter,
    HostBinding,
    Injectable,
    Input,
    OnInit, Output,
} from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { MatFormField, MatFormFieldControl } from '@angular/material/form-field';
import { MatSlideToggleChange } from '@angular/material/slide-toggle';
import { BehaviorSubject } from 'rxjs';
import { distinctUntilChanged, startWith } from 'rxjs/operators';
import { MdsEditorInstanceService, Widget } from '../../mds-editor-instance.service';
import { BulkMode, InputStatus, RequiredMode } from '../../types';
import {MdsEditorWidgetBase, ValueType} from '../mds-editor-widget-base';
import {UIAnimation} from '../../../../../core-module/ui/ui-animation';
import {NativeWidget} from '../../mds-editor-view/mds-editor-view.component';
import {Node} from '../../../../../core-module/rest/data-object';


@Component({
    selector: 'app-mds-editor-widget-file-upload',
    templateUrl: './mds-editor-widget-file-upload.component.html',
    styleUrls: ['./mds-editor-widget-file-upload.component.scss'],
})
export class MdsEditorWidgetFileUploadComponent implements OnInit, NativeWidget {
    static readonly constraints = {
        requiresNode: false,
        supportsBulk: false,
    };
    selectedFiles: File[];
    hasChanges = new BehaviorSubject<boolean>(false);
    isFileOver = false;
    supportsDrop = true;
    link: string;

    @Output() onSetLink = new EventEmitter<string>();

    constructor(
        private mdsEditorInstance: MdsEditorInstanceService,
    ) {

    }

    ngOnInit(): void {
    }

    setLink() {
        this.onSetLink.emit(this.link);
    }
    setFilesByFileList(fileList: FileList) {
        this.selectedFiles = [];
        for(let i = 0;i < fileList.length; i++) {
            this.selectedFiles.push(fileList.item(i));
        }
    }
    filesSelected(files: Event) {
        this.setFilesByFileList((files.target as HTMLInputElement).files);
    }
}