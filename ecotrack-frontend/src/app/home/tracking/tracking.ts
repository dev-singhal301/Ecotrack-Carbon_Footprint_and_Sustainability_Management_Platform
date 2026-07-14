import { Component } from '@angular/core';

@Component({
  selector: 'app-tracking',
  standalone: true,
  imports: [],
  templateUrl: './tracking.html',
  styleUrls: ['./tracking.css']
})
export class Tracking {

  // 👈 Add this method to handle the template's (submit) event listener
  onLogSubmit(event: Event) {
    event.preventDefault(); 
    console.log('Form submission captured locally (No API call yet)!');
  }

}