import { Component, Directive, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  AbstractControl,
  FormsModule,
  NG_VALIDATORS,
  NgForm,
  NgModel,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { RegisterRequest } from '../core/models/auth.model';
import { TranslatePipe } from '../core/pipes/translate.pipe';

const MIN_REGISTER_AGE = 18;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function maximumAdultBirthDate(today = new Date()): string {
  return toIsoDate(
    new Date(today.getFullYear() - MIN_REGISTER_AGE, today.getMonth(), today.getDate()),
  );
}

function adultDateOfBirthErrors(value: unknown, today = new Date()): ValidationErrors | null {
  if (value == null || value === '') {
    return null;
  }
  if (typeof value !== 'string' || !ISO_DATE_PATTERN.test(value)) {
    return { dobInvalid: true };
  }

  const [year, month, day] = value.split('-').map(Number);
  const dob = new Date(year, month - 1, day);
  if (dob.getFullYear() !== year || dob.getMonth() !== month - 1 || dob.getDate() !== day) {
    return { dobInvalid: true };
  }

  let age = today.getFullYear() - year;
  const todayMonth = today.getMonth() + 1;
  const todayDay = today.getDate();
  if (todayMonth < month || (todayMonth === month && todayDay < day)) {
    age -= 1;
  }
  return age < MIN_REGISTER_AGE ? { underage: true } : null;
}

@Directive({
  selector: '[adultDateOfBirth]',
  providers: [{ provide: NG_VALIDATORS, useExisting: AdultDateOfBirthValidator, multi: true }],
})
export class AdultDateOfBirthValidator implements Validator {
  validate(control: AbstractControl): ValidationErrors | null {
    return adultDateOfBirthErrors(control.value);
  }
}

@Component({
    selector: 'app-register',
    imports: [FormsModule, RouterLink, TranslatePipe, AdultDateOfBirthValidator],
    templateUrl: './register.html',
    styleUrl: './register.css',
})
export class Register {
  formData: RegisterRequest = {
    firstName: '',
    lastName: '',
    dateOfBirth: '',
    ssn: '',
    address: '',
    phoneNumber: '',
    email: '',
    password: '',
  };
  messageKey = signal('');
  isError = signal(false);
  showPassword = signal(false);

  get maxDateOfBirth(): string {
    return maximumAdultBirthDate();
  }

  togglePassword(): void {
    this.showPassword.update((visible) => !visible);
  }

  dateOfBirthHasEntryError(model: NgModel): boolean {
    const errors = model.errors;
    return !!errors && (!!errors['underage'] || !!errors['dobInvalid']);
  }

  openDatePicker(input: HTMLInputElement, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    input.focus();
    try {
      input.showPicker?.();
    } catch {
      return;
    }
  }

  constructor(private authService: AuthService, private router: Router) {}

  register(form: NgForm) {
    this.messageKey.set('');
    this.isError.set(false);

    if (form.invalid) {
      this.isError.set(true);
      this.messageKey.set('auth.register_incomplete');
      return;
    }

    const request: RegisterRequest = {
      ...this.formData,
      dateOfBirth: `${this.formData.dateOfBirth}T00:00:00Z`,
    };

    this.authService.register(request).subscribe({
      next: (response) => {
        const target = response.role === 'ROLE_ADMIN'
          ? '/admin'
          : response.role === 'ROLE_ANALYST'
            ? '/analyst'
            : '/client';
        this.router.navigateByUrl(target);
      },
      error: (err: HttpErrorResponse) => {
        this.isError.set(true);
        if (err.status === 409) {
          this.messageKey.set('auth.email_exists');
        } else if (err.status === 400) {
          this.messageKey.set('auth.check_information');
        } else if (err.status === 0) {
          this.messageKey.set('auth.server_unreachable');
        } else {
          this.messageKey.set('auth.generic_error');
        }
      },
    });
  }
}
