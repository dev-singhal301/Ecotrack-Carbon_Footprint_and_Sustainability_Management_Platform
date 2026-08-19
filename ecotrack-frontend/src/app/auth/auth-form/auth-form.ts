import { Component, computed, signal, AfterViewInit, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { notOnlyWhitespace } from '../validators';
import { Auth } from '../auth';

declare const google: any;

const GOOGLE_CLIENT_ID = '1060715159333-01t5hgjdc1ke4ltra4e4edn3562vnjot.apps.googleusercontent.com';

export interface SlideMetric {
  label: string;
  value: string;
}

export interface VisualSlide {
  image: string;
  title: string;
  text: string;
  metrics: SlideMetric[];
}

@Component({
  selector: 'app-auth-form',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './auth-form.html',
  styleUrl: './auth-form.css',
})
export class AuthForm implements AfterViewInit, OnInit, OnDestroy {
  @ViewChild('googleBtn') googleBtnRef!: ElementRef;
  isLoginMode = signal(true);
  isForgotPasswordMode = signal(false);
  isSubmitting = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  showPassword = signal(false);

  // ---- Visual panel slideshow with clean central data ----
  readonly slides: VisualSlide[] = [
    {
      image: '1.avif',
      title: 'The Ozone Layer Is Healing',
      text: 'Global phase-outs under the Montreal Protocol have Earth\'s protective ozone layer on track to fully recover by 2040.',
      metrics: [
        { label: 'Recovery Target', value: '2040' },
        { label: 'Global Accord', value: 'Montreal Protocol' }
      ]
    },
    {
      image: '2.avif',
      title: 'The Air Quality Crisis',
      text: 'Toxic PM2.5 pollution contributes to over 1.6 million premature deaths in India every year, cutting life expectancy across the Indo-Gangetic plain.',
      metrics: [
        { label: 'Annual Impact', value: '1.6M+ Lives' },
        { label: 'Primary Contaminant', value: 'PM2.5' }
      ]
    },
    {
      image: '3.avif',
      title: '99.6% Railway Electrification',
      text: 'Indian Railways has electrified over 99.6% of its broad-gauge network, aiming for a net-zero carbon railway by 2030.',
      metrics: [
        { label: 'Electrified Network', value: '99.6%' },
        { label: 'Net-Zero Goal', value: '2030' }
      ]
    },
    {
      image: '4.avif',
      title: 'A Global Solar Leader',
      text: 'India hosts some of the world\'s largest solar parks, including the 2,245 MW Bhadla Solar Park, and co-founded the 120+ nation International Solar Alliance.',
      metrics: [
        { label: 'Bhadla Capacity', value: '2,245 MW' },
        { label: 'Member Alliance', value: '120+ Nations' }
      ]
    },
    {
      image: '5.avif',
      title: 'The Cost of Extreme Weather',
      text: 'Heatwaves, erratic monsoons, and flooding threaten over 80% of India\'s population, costing billions in lost crops and damaged infrastructure yearly.',
      metrics: [
        { label: 'Population At Risk', value: '> 80%' },
        { label: 'Primary Drivers', value: 'Heatwaves & Floods' }
      ]
    },
    {
      image: '6.avif',
      title: 'The Plastic Pollution Problem',
      text: 'India generates over 3.4 million tonnes of plastic waste annually, much of it choking drainage systems and polluting coastlines.',
      metrics: [
        { label: 'Annual Generation', value: '3.4M Tonnes' },
        { label: 'Critical Zones', value: 'Coasts & Drainage' }
      ]
    },
  ];

  currentSlideIndex = signal(0);
  currentSlide = computed(() => this.slides[this.currentSlideIndex()]);

  private slideshowTimer: ReturnType<typeof setInterval> | null = null;
  private readonly SLIDE_INTERVAL_MS = 5000;

  authForm: FormGroup;

  headerText = computed(() => {
    if (this.isForgotPasswordMode()) {
      return 'Reset Password';
    }
    return this.isLoginMode() ? 'Welcome Back' : 'Sign Up for Free';
  });

  submitLabel = computed(() => {
    if (this.isSubmitting()) {
      if (this.isForgotPasswordMode()) return 'Sending request...';
      return this.isLoginMode() ? 'Logging in...' : 'Registering...';
    }
    if (this.isForgotPasswordMode()) return 'SEND RESET LINK';
    return this.isLoginMode() ? 'LOGIN' : 'GET STARTED';
  });

  constructor(private fb: FormBuilder, private auth: Auth, private router: Router, private route: ActivatedRoute) {
    this.authForm = this.fb.group({
      name: [''],
      email: ['', [
        Validators.required,
        Validators.email,
        Validators.pattern(/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/)
      ]],
      password: ['', [Validators.required, Validators.minLength(6), notOnlyWhitespace]],
    });
    this.updateValidators();
  }

  ngOnInit(): void {
    const sessionMessage = this.route.snapshot.queryParamMap.get('sessionMessage');
    if (sessionMessage) {
      this.errorMessage.set(sessionMessage);
      this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    }

    // Cross-tab handoff: if this tab is left sitting on the "check your
    // email" screen while the person clicks the reset link in a NEW tab
    // (which they always will — Gmail opens links in a separate browsing
    // context we have no control over), this tab has no idea anything
    // happened. Auth.login() writes the JWT to localStorage on success,
    // and the browser natively fires a `storage` event in every OTHER
    // same-origin tab when that happens. Listening for it lets this tab
    // catch up on its own once the reset+login completes elsewhere,
    // instead of sitting there stale.
    window.addEventListener('storage', this.handleCrossTabLogin);
    this.startSlideshow();
  }

  ngOnDestroy(): void {
    this.stopSlideshow();
  }

  private handleCrossTabLogin = (event: StorageEvent): void => {
    if (event.key !== 'ecotrack_auth_token' || !event.newValue) {
      return;
    }
    // Someone just logged in from another tab (e.g. after finishing a
    // password reset there). Route this tab to the right home page too.
    this.router.navigate([this.auth.isAdmin() ? '/home-admin' : '/home']);
  };

  ngAfterViewInit(): void {
    this.waitForGoogleScript();
  }

  private startSlideshow(): void {
    this.stopSlideshow();
    this.slideshowTimer = setInterval(() => {
      this.currentSlideIndex.update(i => (i + 1) % this.slides.length);
    }, this.SLIDE_INTERVAL_MS);
  }

  private stopSlideshow(): void {
    if (this.slideshowTimer) {
      clearInterval(this.slideshowTimer);
      this.slideshowTimer = null;
    }
  }

  pauseSlideshow(): void {
    this.stopSlideshow();
  }

  resumeSlideshow(): void {
    this.startSlideshow();
  }

  goToSlide(index: number): void {
    this.currentSlideIndex.set(index);
    this.startSlideshow();
  }

  private waitForGoogleScript(retries: number = 20): void {
    if (typeof google !== 'undefined' && google.accounts) {
      google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: (response: any) => this.handleGoogleCredentialResponse(response),
      });
      google.accounts.id.renderButton(this.googleBtnRef.nativeElement, {
        theme: 'outline',
        size: 'large',
        width: 300,
      });
      return;
    }

    if (retries <= 0) {
      console.error('Google Identity Services script failed to load after waiting.');
      return;
    }

    setTimeout(() => this.waitForGoogleScript(retries - 1), 100);
  }

  handleGoogleCredentialResponse(response: any): void {
    this.errorMessage.set('');
    this.successMessage.set('');
    this.isSubmitting.set(true);

    this.auth.googleLogin(response.credential).subscribe({
      next: (authResponse) => {
        this.isSubmitting.set(false);
        if (!authResponse.hasPassword) {
          this.router.navigate(['/set-password']);
        } else if (this.auth.isAdmin()) {
          this.router.navigate(['/home-admin']);
        } else {
          this.router.navigate(['/home']);
        }
      },
      error: (err) => {
        this.isSubmitting.set(false);
        const msg = err.error?.message || err.message || 'Google sign-in failed. Please try again.';
        this.errorMessage.set(msg);
      },
    });
  }

  switchMode(toLogin: boolean): void {
    if (this.isLoginMode() === toLogin && !this.isForgotPasswordMode()) {
      return;
    }
    this.isForgotPasswordMode.set(false);
    this.isLoginMode.set(toLogin);
    this.errorMessage.set('');
    this.successMessage.set('');
    this.authForm.reset();
    this.updateValidators();
  }

  togglePasswordVisibility(): void {
    this.showPassword.update(v => !v);
  }

  /**
   * Controls the in-place slide state transition overlay to handle forgot password logic
   */
  toggleForgotPassword(show: boolean): void {
    this.isForgotPasswordMode.set(show);
    this.errorMessage.set('');
    this.successMessage.set('');

    // Clear the validators and reset fields safely
    this.authForm.get('name')?.setValue('');
    this.authForm.get('password')?.setValue('');
    this.updateValidators();
  }

  private updateValidators(): void {
    const nameControl = this.authForm.get('name');
    const passwordControl = this.authForm.get('password');

    // If password state is active, only email is validated
    if (this.isForgotPasswordMode()) {
      nameControl?.clearValidators();
      passwordControl?.clearValidators();
    } else {
      passwordControl?.setValidators([Validators.required, Validators.minLength(6), notOnlyWhitespace]);
      if (this.isLoginMode()) {
        nameControl?.clearValidators();
      } else {
        nameControl?.setValidators([Validators.required, Validators.minLength(2)]);
      }
    }

    nameControl?.updateValueAndValidity();
    passwordControl?.updateValueAndValidity();
  }

  /**
   * Separate handler method dedicated to sending the validated password recovery payload
   */
  handleForgotPasswordRequest(): void {
    const emailControl = this.authForm.get('email');

    if (emailControl?.invalid) {
      emailControl.markAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');

    const email = emailControl?.value;

    this.auth.forgotPassword(email).subscribe({
      next: (res: any) => {
        this.isSubmitting.set(false);
        this.successMessage.set(res?.message || 'A secure password recovery link has been generated.');
      },
      error: (err) => {
        this.isSubmitting.set(false);
        const msg = err.error?.message || err.message || 'Recovery attempt failed. Please check the email entered.';
        this.errorMessage.set(msg);
      }
    });
  }

  onSubmit(): void {
    if (this.isForgotPasswordMode()) {
      this.handleForgotPasswordRequest();
      return;
    }

    this.errorMessage.set('');
    this.successMessage.set('');

    if (this.authForm.invalid) {
      this.authForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);

    if (this.isLoginMode()) {
      const { email, password } = this.authForm.value;
      this.auth.login({ email, password }).subscribe({
        next: () => {
          this.isSubmitting.set(false);
          if (this.auth.isAdmin()) {
            this.router.navigate(['/home-admin']);
          } else {
            this.router.navigate(['/home']);
          }
        },
        error: (err) => {
          this.isSubmitting.set(false);
          const msg = err.error?.message || err.message || 'Login failed. Check your credentials.';
          this.errorMessage.set(msg);
        },
      });
    } else {
      const { name, email, password } = this.authForm.value;
      this.auth.register({ name, email, password }).subscribe({
        next: () => {
          this.isSubmitting.set(false);
          this.switchMode(true);
          this.authForm.patchValue({ email });
          this.successMessage.set('Account created. Please log in.');
        },
        error: (err) => {
          this.isSubmitting.set(false);
          const msg = err.error?.message || err.message || 'Registration failed.';
          this.errorMessage.set(msg);
        },
      });
    }
  }
}